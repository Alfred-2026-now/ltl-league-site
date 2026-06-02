package com.ltl.league.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ltl.league.admin.dto.*;
import com.ltl.league.admin.service.AdminMatchResultService;
import com.ltl.league.admin.service.AdminMatchService;
import com.ltl.league.admin.service.MatchResultValidator;
import com.ltl.league.admin.service.MatchSettlementService;
import com.ltl.league.entity.*;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.*;
import com.ltl.league.service.TeamService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AdminMatchResultServiceImpl implements AdminMatchResultService {

    private final MatchMapper matchMapper;
    private final MatchResultMapper matchResultMapper;
    private final GameMapper gameMapper;
    private final AttachmentMapper attachmentMapper;
    private final PLedgerMapper pLedgerMapper;
    private final ValuationChangeMapper valuationChangeMapper;
    private final TeamService teamService;
    private final AdminMatchService adminMatchService;
    private final MatchSettlementService matchSettlementService;

    @Value("${ltl.match.bo5-enabled:false}")
    private boolean bo5Enabled;

    @Value("${ltl.upload.dir:/var/www/ltl-league/uploads}")
    private String uploadDir;

    @Value("${ltl.upload.url-prefix:/uploads}")
    private String uploadUrlPrefix;

    public AdminMatchResultServiceImpl(
            MatchMapper matchMapper,
            MatchResultMapper matchResultMapper,
            GameMapper gameMapper,
            AttachmentMapper attachmentMapper,
            PLedgerMapper pLedgerMapper,
            ValuationChangeMapper valuationChangeMapper,
            TeamService teamService,
            AdminMatchService adminMatchService,
            MatchSettlementService matchSettlementService) {
        this.matchMapper = matchMapper;
        this.matchResultMapper = matchResultMapper;
        this.gameMapper = gameMapper;
        this.attachmentMapper = attachmentMapper;
        this.pLedgerMapper = pLedgerMapper;
        this.valuationChangeMapper = valuationChangeMapper;
        this.teamService = teamService;
        this.adminMatchService = adminMatchService;
        this.matchSettlementService = matchSettlementService;
    }

    @Override
    public MatchResultVO getResultContext(Long matchId) {
        Match match = adminMatchService.getByIdOrThrow(matchId);

        MatchResult draft = findDraft(matchId);
        if (draft != null) {
            return toVO(draft, match, false);
        }

        MatchResult published = findPublished(matchId);
        if (published != null) {
            MatchResultVO vo = toVO(published, match, true);
            vo.setCanCreateDraft(true);
            return vo;
        }

        MatchResultVO empty = new MatchResultVO();
        empty.setMatchId(matchId);
        empty.setReadOnly(false);
        empty.setCanCreateDraft(true);
        empty.setStatus("none");
        empty.setGames(Collections.emptyList());
        return empty;
    }

    @Override
    @Transactional
    public MatchResultVO createDraft(Long matchId, MatchResultDraftRequest request) {
        Match match = adminMatchService.getByIdOrThrow(matchId);
        if (findDraft(matchId) != null) {
            throw new BusinessException(400, "已存在赛果草稿，请编辑现有草稿");
        }
        if (findPublished(matchId) != null) {
            throw new BusinessException(400, "已有已发布赛果，请先撤回后再新建草稿");
        }

        Team home = requireTeam(match.getHomeTeamId());
        Team away = requireTeam(match.getAwayTeamId());
        normalizeForfeitRequest(request, home, away);
        MatchResultValidator.validateDraft(match.getFormat(), bo5Enabled, request, home, away);

        MatchResult result = new MatchResult();
        result.setMatchId(matchId);
        result.setVersionNo(nextVersionNo(matchId));
        result.setStatus("draft");
        applyRequestToResult(result, match, request, home, away);
        matchResultMapper.insert(result);
        matchSettlementService.syncInputs(match, result, request.getSettlement());

        gameMapper.physicalDeleteByMatchId(matchId);
        syncGames(match, result, home, away, request.getGames());
        return toVO(result, match, false);
    }

    @Override
    @Transactional
    public MatchResultVO updateDraft(Long matchId, Long resultId, MatchResultDraftRequest request) {
        Match match = adminMatchService.getByIdOrThrow(matchId);
        MatchResult result = getDraftOrThrow(matchId, resultId);

        Team home = requireTeam(match.getHomeTeamId());
        Team away = requireTeam(match.getAwayTeamId());
        normalizeForfeitRequest(request, home, away);
        MatchResultValidator.validateDraft(match.getFormat(), bo5Enabled, request, home, away);

        applyRequestToResult(result, match, request, home, away);
        matchResultMapper.updateById(result);
        matchSettlementService.syncInputs(match, result, request.getSettlement());

        resyncGamesPreservingScreenshots(match, result, home, away, request.getGames());
        return toVO(result, match, false);
    }

    @Override
    public SettlementPreviewVO previewSettlement(Long matchId, MatchResultDraftRequest request) {
        Match match = adminMatchService.getByIdOrThrow(matchId);
        Team home = requireTeam(match.getHomeTeamId());
        Team away = requireTeam(match.getAwayTeamId());
        normalizeForfeitRequest(request, home, away);
        MatchResult result = new MatchResult();
        applyRequestToResult(result, match, request, home, away);
        return matchSettlementService.preview(match, result, request.getSettlement());
    }

    @Override
    @Transactional
    public void publish(Long matchId, Long resultId) {
        Match match = adminMatchService.getByIdOrThrow(matchId);
        MatchResult result = getDraftOrThrow(matchId, resultId);

        if (findPublished(matchId) != null) {
            throw new BusinessException(400, "该比赛已有已发布赛果");
        }

        Team home = requireTeam(match.getHomeTeamId());
        Team away = requireTeam(match.getAwayTeamId());
        MatchResultDraftRequest req = toRequest(result, matchId, resultId);
        MatchResultValidator.validateForPublish(match.getFormat(), bo5Enabled, req, home, away);
        matchSettlementService.apply(match, result);

        result.setStatus("published");
        result.setPublishedAt(LocalDateTime.now());
        matchResultMapper.updateById(result);

        match.setHomeScore(result.getHomeScore());
        match.setAwayScore(result.getAwayScore());
        match.setHomePoints(result.getHomePoints());
        match.setAwayPoints(result.getAwayPoints());
        match.setResultPublished(1);
        match.setVersion("r" + result.getVersionNo());
        match.setSource("manual_entry");

        if ("forfeit".equals(result.getResultType())) {
            match.setStatus("forfeit");
            if (result.getWinnerTeamId() != null) {
                Long loserId = result.getWinnerTeamId().equals(home.getId()) ? away.getId() : home.getId();
                match.setForfeitTeamId(loserId);
            }
        } else {
            match.setStatus("finished");
            match.setForfeitTeamId(null);
        }
        matchMapper.updateById(match);
    }

    @Override
    @Transactional
    public MatchResultVO withdraw(Long matchId, Long resultId, String withdrawReason) {
        Match match = adminMatchService.getByIdOrThrow(matchId);
        MatchResult result = matchResultMapper.selectById(resultId);
        if (result == null || !result.getMatchId().equals(matchId)) {
            throw new BusinessException(404, "赛果不存在");
        }
        if (!"published".equals(result.getStatus())) {
            throw new BusinessException(400, "只能撤回已发布的赛果");
        }
        if (withdrawReason == null || withdrawReason.isBlank()) {
            throw new BusinessException(400, "必须填写撤回原因");
        }
        if (findDraft(matchId) != null) {
            throw new BusinessException(400, "已存在赛果草稿，请先处理后再撤回");
        }

        Team home = requireTeam(match.getHomeTeamId());
        Team away = requireTeam(match.getAwayTeamId());
        MatchResultDraftRequest snapshot = toRequest(result, matchId, resultId);
        matchSettlementService.rollback(match, result);

        List<Game> oldGames = gameMapper.selectList(new LambdaQueryWrapper<Game>()
                .eq(Game::getResultId, resultId)
                .orderByAsc(Game::getGameIndex));
        Map<Long, Integer> gameIdToIndex = oldGames.stream()
                .collect(Collectors.toMap(Game::getId, Game::getGameIndex, (a, b) -> a));

        List<Attachment> screenshots = attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getResultId, resultId)
                .eq(Attachment::getType, "score_screenshot")
                .eq(Attachment::getDeleted, 0));

        result.setStatus("withdrawn");
        result.setWithdrawnAt(LocalDateTime.now());
        result.setWithdrawReason(withdrawReason.trim());
        matchResultMapper.updateById(result);

        match.setResultPublished(0);
        match.setHomeScore(null);
        match.setAwayScore(null);
        match.setHomePoints(null);
        match.setAwayPoints(null);
        match.setForfeitTeamId(null);
        match.setSource(null);
        match.setVersion(null);
        if ("forfeit".equals(match.getStatus()) || "finished".equals(match.getStatus())) {
            match.setStatus("scheduled");
        }
        matchMapper.updateById(match);

        attachmentMapper.update(null, new LambdaUpdateWrapper<Attachment>()
                .eq(Attachment::getResultId, resultId)
                .set(Attachment::getGameId, null)
                .set(Attachment::getIsVoided, 1));

        physicalRemoveGamesForResult(resultId);

        MatchResult draft = new MatchResult();
        draft.setMatchId(matchId);
        draft.setVersionNo(nextVersionNo(matchId));
        draft.setStatus("draft");
        applyRequestToResult(draft, match, snapshot, home, away);
        matchResultMapper.insert(draft);

        syncGames(match, draft, home, away, snapshot.getGames());
        cloneScreenshots(matchId, draft.getId(), screenshots, gameIdToIndex);

        return toVO(draft, match, false);
    }

    @Override
    @Transactional
    public MatchResultAttachmentVO uploadScreenshot(Long matchId, Long resultId, Integer gameIndex, MultipartFile file) {
        MatchResult result = getScreenshotUploadResultOrThrow(matchId, resultId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择文件");
        }

        Game game = findGameForResult(resultId, gameIndex);
        if (game == null) {
            throw new BusinessException(400, "请先填写并保存第 " + gameIndex + " 局小局信息后再上传截图");
        }

        String ext = guessExt(file.getOriginalFilename());
        String filename = UUID.randomUUID() + ext;
        Path dir = Path.of(uploadDir, String.valueOf(matchId), String.valueOf(resultId), "game-" + gameIndex);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败: " + e.getMessage());
        }

        String url = uploadUrlPrefix + "/" + matchId + "/" + resultId + "/game-" + gameIndex + "/" + filename;
        Attachment att = new Attachment();
        att.setMatchId(matchId);
        att.setResultId(resultId);
        att.setGameId(game.getId());
        att.setType("score_screenshot");
        att.setLabel("第" + gameIndex + "局战绩截图");
        att.setUrl(url);
        att.setFilePath(dir.resolve(filename).toString());
        att.setIsVoided(0);
        attachmentMapper.insert(att);

        MatchResultAttachmentVO vo = new MatchResultAttachmentVO();
        vo.setId(att.getId());
        vo.setLabel(att.getLabel());
        vo.setUrl(att.getUrl());
        return vo;
    }

    @Override
    @Transactional
    public void deleteAttachment(Long attachmentId) {
        Attachment att = attachmentMapper.selectById(attachmentId);
        if (att == null) {
            throw new BusinessException(404, "附件不存在");
        }
        MatchResult linked = att.getResultId() != null ? matchResultMapper.selectById(att.getResultId()) : null;
        if (linked != null && !"draft".equals(linked.getStatus())) {
            throw new BusinessException(400, "已发布赛果的截图不能删除");
        }
        if (linked != null && "draft".equals(linked.getStatus())) {
            attachmentMapper.physicalDeleteById(attachmentId);
            return;
        }
        attachmentMapper.deleteById(attachmentId);
    }

    private MatchResult findDraft(Long matchId) {
        return matchResultMapper.selectOne(new LambdaQueryWrapper<MatchResult>()
                .eq(MatchResult::getMatchId, matchId)
                .eq(MatchResult::getStatus, "draft")
                .eq(MatchResult::getDeleted, 0)
                .orderByDesc(MatchResult::getVersionNo)
                .last("LIMIT 1"));
    }

    private MatchResult findPublished(Long matchId) {
        return matchResultMapper.selectOne(new LambdaQueryWrapper<MatchResult>()
                .eq(MatchResult::getMatchId, matchId)
                .eq(MatchResult::getStatus, "published")
                .eq(MatchResult::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private int nextVersionNo(Long matchId) {
        List<MatchResult> all = matchResultMapper.selectList(new LambdaQueryWrapper<MatchResult>()
                .eq(MatchResult::getMatchId, matchId)
                .eq(MatchResult::getDeleted, 0));
        return all.stream().map(MatchResult::getVersionNo).filter(Objects::nonNull).max(Integer::compareTo).orElse(0) + 1;
    }

    private MatchResult getDraftOrThrow(Long matchId, Long resultId) {
        MatchResult result = matchResultMapper.selectById(resultId);
        if (result == null || !result.getMatchId().equals(matchId)) {
            throw new BusinessException(404, "赛果不存在");
        }
        if (!"draft".equals(result.getStatus())) {
            throw new BusinessException(400, "只能编辑草稿状态的赛果");
        }
        return result;
    }

    private MatchResult getScreenshotUploadResultOrThrow(Long matchId, Long resultId) {
        MatchResult result = matchResultMapper.selectById(resultId);
        if (result == null || !result.getMatchId().equals(matchId)) {
            throw new BusinessException(404, "赛果不存在");
        }
        if (!"draft".equals(result.getStatus()) && !"published".equals(result.getStatus())) {
            throw new BusinessException(400, "只能为草稿或已发布赛果上传截图");
        }
        return result;
    }

    private Team requireTeam(Long teamId) {
        Team team = teamService.getById(teamId);
        if (team == null) {
            throw new BusinessException(400, "队伍不存在: " + teamId);
        }
        return team;
    }

    private void normalizeForfeitRequest(MatchResultDraftRequest request, Team home, Team away) {
        if (!"forfeit".equalsIgnoreCase(request.getResultType() != null ? request.getResultType() : "")) {
            return;
        }
        if (request.getWinnerTeamId() == null) {
            return;
        }
        if (request.getWinnerTeamId().equals(home.getId())) {
            request.setHomeScore(2);
            request.setAwayScore(0);
        } else if (request.getWinnerTeamId().equals(away.getId())) {
            request.setHomeScore(0);
            request.setAwayScore(2);
        }
    }

    private void applyRequestToResult(MatchResult result, Match match, MatchResultDraftRequest request, Team home, Team away) {
        String resultType = request.getResultType() != null && !request.getResultType().isBlank()
                ? request.getResultType().trim().toLowerCase() : "normal";
        result.setResultType(resultType);

        int homeScore = request.getHomeScore() != null ? request.getHomeScore() : 0;
        int awayScore = request.getAwayScore() != null ? request.getAwayScore() : 0;
        if ("forfeit".equals(resultType) && request.getWinnerTeamId() != null) {
            if (request.getWinnerTeamId().equals(home.getId())) {
                homeScore = 2;
                awayScore = 0;
            } else {
                homeScore = 0;
                awayScore = 2;
            }
        }
        result.setHomeScore(homeScore);
        result.setAwayScore(awayScore);
        result.setHomePoints(request.getHomePoints());
        result.setAwayPoints(request.getAwayPoints());
        result.setNotes(blankToNull(request.getNotes()));

        if ("BO2".equalsIgnoreCase(match.getFormat())
                && homeScore == 1 && awayScore == 1) {
            result.setWinnerTeamId(null);
        } else {
            result.setWinnerTeamId(request.getWinnerTeamId());
        }
    }

    private void syncGames(Match match, MatchResult result, Team home, Team away, List<GameDraftDTO> games) {
        if (games == null || games.isEmpty()) return;
        for (GameDraftDTO dto : games) {
            if (dto.getGameIndex() == null || dto.getWinner() == null || dto.getWinner().isBlank()) {
                continue;
            }
            Game game = new Game();
            game.setMatchId(match.getId());
            game.setResultId(result.getId());
            game.setGameIndex(dto.getGameIndex());
            game.setWinner(dto.getWinner().trim());
            game.setBlueTeam(dto.getBlueTeam() != null ? dto.getBlueTeam().trim() : "");
            game.setRedTeam(dto.getRedTeam() != null ? dto.getRedTeam().trim() : "");
            game.setHomeTeam(home.getState());
            game.setAwayTeam(away.getState());
            game.setDurationSeconds(dto.getDurationSeconds());
            game.setSourceGameId(blankToNull(dto.getSourceGameId()));
            game.setGameVersion(blankToNull(dto.getGameVersion()));
            gameMapper.insert(game);
        }
    }

    private Game findGameForResult(Long resultId, Integer gameIndex) {
        return gameMapper.selectOne(new LambdaQueryWrapper<Game>()
                .eq(Game::getResultId, resultId)
                .eq(Game::getGameIndex, gameIndex)
                .eq(Game::getDeleted, 0)
                .last("LIMIT 1"));
    }

    /** 撤回等场景：删除小局并清理截图记录 */
    private void physicalRemoveGamesForResult(Long resultId) {
        attachmentMapper.physicalDeleteScreenshotsByResultId(resultId);
        gameMapper.physicalDeleteByResultId(resultId);
    }

    private void physicalDeleteGamesOnly(Long resultId) {
        gameMapper.physicalDeleteByResultId(resultId);
    }

    /**
     * 更新草稿时重建小局，但保留已上传截图并挂到新 game_id。
     * 此前每次保存草稿会删掉全部截图，导致发布后前台看不到图。
     */
    private void resyncGamesPreservingScreenshots(
            Match match, MatchResult result, Team home, Team away, List<GameDraftDTO> games) {
        Long resultId = result.getId();
        Map<Integer, List<Attachment>> preserved = loadActiveScreenshotsByGameIndex(resultId);
        detachScreenshotGameLinks(resultId);
        physicalDeleteGamesOnly(resultId);
        syncGames(match, result, home, away, games);
        relinkScreenshots(resultId, preserved);
    }

    /** 删除小局前解除截图外键（含逻辑删除的附件，否则会触发 fk_attachment_game） */
    private void detachScreenshotGameLinks(Long resultId) {
        attachmentMapper.detachAllScreenshotGameLinks(resultId);
    }

    private Map<Integer, List<Attachment>> loadActiveScreenshotsByGameIndex(Long resultId) {
        List<Game> games = gameMapper.selectList(new LambdaQueryWrapper<Game>()
                .eq(Game::getResultId, resultId));
        Map<Long, Integer> gameIdToIndex = games.stream()
                .collect(Collectors.toMap(Game::getId, Game::getGameIndex, (a, b) -> a));

        List<Attachment> attachments = attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getResultId, resultId)
                .eq(Attachment::getType, "score_screenshot")
                .eq(Attachment::getIsVoided, 0)
                .eq(Attachment::getDeleted, 0));

        Map<Integer, List<Attachment>> byIndex = new HashMap<>();
        for (Attachment att : attachments) {
            Integer index = att.getGameId() != null ? gameIdToIndex.get(att.getGameId()) : null;
            if (index == null) {
                index = parseGameIndexFromUrl(att.getUrl());
            }
            if (index == null) {
                continue;
            }
            byIndex.computeIfAbsent(index, k -> new ArrayList<>()).add(att);
        }
        return byIndex;
    }

    private void relinkScreenshots(Long resultId, Map<Integer, List<Attachment>> preserved) {
        if (preserved.isEmpty()) {
            return;
        }
        List<Game> newGames = gameMapper.selectList(new LambdaQueryWrapper<Game>()
                .eq(Game::getResultId, resultId)
                .orderByAsc(Game::getGameIndex));
        Map<Integer, Long> indexToGameId = newGames.stream()
                .collect(Collectors.toMap(Game::getGameIndex, Game::getId, (a, b) -> a));

        for (Map.Entry<Integer, List<Attachment>> entry : preserved.entrySet()) {
            Long newGameId = indexToGameId.get(entry.getKey());
            if (newGameId == null) {
                continue;
            }
            for (Attachment att : entry.getValue()) {
                attachmentMapper.update(null, new LambdaUpdateWrapper<Attachment>()
                        .eq(Attachment::getId, att.getId())
                        .set(Attachment::getGameId, newGameId)
                        .set(Attachment::getIsVoided, 0));
            }
        }
    }

    private static final Pattern GAME_INDEX_IN_URL = Pattern.compile("/game-(\\d+)/");

    private Integer parseGameIndexFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher matcher = GAME_INDEX_IN_URL.matcher(url);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private void cloneScreenshots(
            Long matchId,
            Long newResultId,
            List<Attachment> sources,
            Map<Long, Integer> gameIdToIndex) {
        if (sources == null || sources.isEmpty()) {
            return;
        }
        List<Game> newGames = gameMapper.selectList(new LambdaQueryWrapper<Game>()
                .eq(Game::getResultId, newResultId)
                .orderByAsc(Game::getGameIndex));
        Map<Integer, Long> indexToGameId = newGames.stream()
                .collect(Collectors.toMap(Game::getGameIndex, Game::getId, (a, b) -> a));

        for (Attachment src : sources) {
            if (src.getGameId() == null) {
                continue;
            }
            Integer gameIndex = gameIdToIndex.get(src.getGameId());
            if (gameIndex == null) {
                continue;
            }
            Long newGameId = indexToGameId.get(gameIndex);
            if (newGameId == null) {
                continue;
            }
            Attachment copy = new Attachment();
            copy.setMatchId(matchId);
            copy.setResultId(newResultId);
            copy.setGameId(newGameId);
            copy.setType(src.getType());
            copy.setLabel(src.getLabel());
            copy.setUrl(src.getUrl());
            copy.setFilePath(src.getFilePath());
            copy.setNote(src.getNote());
            copy.setIsVoided(0);
            attachmentMapper.insert(copy);
        }
    }

    private MatchResultDraftRequest toRequest(MatchResult result, Long matchId, Long resultId) {
        MatchResultDraftRequest req = new MatchResultDraftRequest();
        req.setResultType(result.getResultType());
        req.setHomeScore(result.getHomeScore());
        req.setAwayScore(result.getAwayScore());
        req.setWinnerTeamId(result.getWinnerTeamId());
        req.setHomePoints(result.getHomePoints());
        req.setAwayPoints(result.getAwayPoints());
        req.setNotes(result.getNotes());
        req.setSettlement(matchSettlementService.loadInputs(matchId, resultId));
        List<Game> games = gameMapper.selectList(new LambdaQueryWrapper<Game>()
                .eq(Game::getResultId, resultId)
                .orderByAsc(Game::getGameIndex));
        req.setGames(games.stream().map(g -> {
            GameDraftDTO dto = new GameDraftDTO();
            dto.setGameIndex(g.getGameIndex());
            dto.setWinner(g.getWinner());
            dto.setBlueTeam(g.getBlueTeam());
            dto.setRedTeam(g.getRedTeam());
            dto.setDurationSeconds(g.getDurationSeconds());
            dto.setSourceGameId(g.getSourceGameId());
            dto.setGameVersion(g.getGameVersion());
            return dto;
        }).collect(Collectors.toList()));
        return req;
    }

    private MatchResultVO toVO(MatchResult result, Match match, boolean readOnly) {
        MatchResultVO vo = new MatchResultVO();
        vo.setId(result.getId());
        vo.setMatchId(result.getMatchId());
        vo.setVersionNo(result.getVersionNo());
        vo.setStatus(result.getStatus());
        vo.setResultType(result.getResultType());
        vo.setHomeScore(result.getHomeScore());
        vo.setAwayScore(result.getAwayScore());
        vo.setWinnerTeamId(result.getWinnerTeamId());
        if (result.getWinnerTeamId() != null) {
            Team w = teamService.getById(result.getWinnerTeamId());
            vo.setWinnerTeamState(w != null ? w.getState() : "");
        }
        vo.setHomePoints(result.getHomePoints());
        vo.setAwayPoints(result.getAwayPoints());
        vo.setNotes(result.getNotes());
        vo.setPublishedAt(result.getPublishedAt() != null ? result.getPublishedAt().toString() : null);
        vo.setWithdrawnAt(result.getWithdrawnAt() != null ? result.getWithdrawnAt().toString() : null);
        vo.setWithdrawReason(result.getWithdrawReason());
        vo.setReadOnly(readOnly);
        vo.setCanCreateDraft(false);
        if (result.getId() != null) {
            vo.setSettlement(matchSettlementService.loadInputs(match.getId(), result.getId()));
        }

        if (result.getId() != null) {
            List<Game> games = gameMapper.selectList(new LambdaQueryWrapper<Game>()
                    .eq(Game::getResultId, result.getId())
                    .orderByAsc(Game::getGameIndex));
            vo.setGames(games.stream().map(g -> toGameVO(g, result.getId())).collect(Collectors.toList()));
        } else {
            vo.setGames(Collections.emptyList());
        }
        return vo;
    }

    private MatchResultGameVO toGameVO(Game game, Long resultId) {
        MatchResultGameVO gvo = new MatchResultGameVO();
        gvo.setId(game.getId());
        gvo.setGameIndex(game.getGameIndex());
        gvo.setWinner(game.getWinner());
        gvo.setBlueTeam(game.getBlueTeam());
        gvo.setRedTeam(game.getRedTeam());
        gvo.setDurationSeconds(game.getDurationSeconds());
        gvo.setSourceGameId(game.getSourceGameId());
        gvo.setGameVersion(game.getGameVersion());

        List<Attachment> screenshots = attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getResultId, resultId)
                .eq(Attachment::getGameId, game.getId())
                .eq(Attachment::getType, "score_screenshot")
                .eq(Attachment::getIsVoided, 0)
                .eq(Attachment::getDeleted, 0));
        gvo.setScreenshots(screenshots.stream().map(a -> {
            MatchResultAttachmentVO av = new MatchResultAttachmentVO();
            av.setId(a.getId());
            av.setLabel(a.getLabel());
            av.setUrl(a.getUrl());
            av.setNote(a.getNote());
            return av;
        }).collect(Collectors.toList()));
        return gvo;
    }

    private String blankToNull(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }

    private String guessExt(String name) {
        if (name == null) return ".jpg";
        int i = name.lastIndexOf('.');
        if (i < 0) return ".jpg";
        String ext = name.substring(i).toLowerCase();
        if (ext.matches("\\.(jpg|jpeg|png|gif|webp)")) return ext;
        return ".jpg";
    }
}
