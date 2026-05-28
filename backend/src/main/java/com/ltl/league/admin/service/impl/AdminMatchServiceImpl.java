package com.ltl.league.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ltl.league.admin.dto.AdminMatchListItemVO;
import com.ltl.league.admin.dto.MatchCreateRequest;
import com.ltl.league.admin.dto.MatchUpdateRequest;
import com.ltl.league.admin.service.AdminMatchService;
import com.ltl.league.entity.Match;
import com.ltl.league.entity.MatchResult;
import com.ltl.league.entity.Team;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.MatchMapper;
import com.ltl.league.mapper.MatchResultMapper;
import com.ltl.league.service.TeamService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminMatchServiceImpl extends ServiceImpl<MatchMapper, Match> implements AdminMatchService {

    private final TeamService teamService;
    private final MatchResultMapper matchResultMapper;

    @Value("${ltl.league.current-season:s1}")
    private String currentSeason;

    @Value("${ltl.match.bo5-enabled:false}")
    private boolean bo5Enabled;

    public AdminMatchServiceImpl(TeamService teamService, MatchResultMapper matchResultMapper) {
        this.teamService = teamService;
        this.matchResultMapper = matchResultMapper;
    }

    @Override
    public List<AdminMatchListItemVO> list(String season,
                                          Integer round,
                                          Long teamId,
                                          String format,
                                          Integer schedulePublished,
                                          String status) {
        LambdaQueryWrapper<Match> qw = new LambdaQueryWrapper<Match>()
                .eq(Match::getDeleted, 0)
                .orderByDesc(Match::getUpdatedAt)
                .orderByDesc(Match::getId);

        if (season != null && !season.isBlank()) {
            qw.eq(Match::getSeason, season.trim());
        }
        if (round != null) {
            qw.eq(Match::getRound, round);
        }
        if (format != null && !format.isBlank()) {
            qw.eq(Match::getFormat, format.trim());
        }
        if (schedulePublished != null) {
            qw.eq(Match::getSchedulePublished, schedulePublished);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(Match::getStatus, status.trim());
        }
        if (teamId != null) {
            qw.and(w -> w.eq(Match::getHomeTeamId, teamId).or().eq(Match::getAwayTeamId, teamId));
        }

        List<Match> matches = list(qw);

        Set<Long> matchIds = matches.stream().map(Match::getId).collect(Collectors.toSet());
        Set<Long> draftMatchIds = matchIds.isEmpty()
                ? Set.of()
                : matchResultMapper.selectList(
                        new LambdaQueryWrapper<MatchResult>()
                                .select(MatchResult::getMatchId)
                                .in(MatchResult::getMatchId, matchIds)
                                .eq(MatchResult::getStatus, "draft")
                                .eq(MatchResult::getDeleted, 0)
                ).stream().map(MatchResult::getMatchId).collect(Collectors.toSet());

        return matches.stream().map(match -> {
            Team home = teamService.getById(match.getHomeTeamId());
            Team away = teamService.getById(match.getAwayTeamId());

            AdminMatchListItemVO vo = new AdminMatchListItemVO();
            vo.setId(match.getId());
            vo.setMatchId(match.getMatchId());
            vo.setSeason(match.getSeason());
            vo.setRound(match.getRound());
            vo.setRoundLabel(match.getRoundLabel());
            vo.setMatchDate(match.getMatchDate() != null ? match.getMatchDate().toString() : null);
            vo.setFormat(match.getFormat());
            vo.setStatus(match.getStatus());
            vo.setHomeTeamId(match.getHomeTeamId());
            vo.setHomeTeamState(home != null ? home.getState() : "");
            vo.setAwayTeamId(match.getAwayTeamId());
            vo.setAwayTeamState(away != null ? away.getState() : "");
            vo.setLiveUrl(match.getLiveUrl());
            vo.setNotes(match.getNotes());
            vo.setSchedulePublished(match.getSchedulePublished());

            boolean publishedResult = match.getResultPublished() != null && match.getResultPublished() == 1;
            vo.setHasPublishedResult(publishedResult);
            vo.setHasResultDraft(draftMatchIds.contains(match.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public Match getByIdOrThrow(Long id) {
        Match match = getById(id);
        if (match == null || (match.getDeleted() != null && match.getDeleted() == 1)) {
            throw new BusinessException(404, "比赛不存在: " + id);
        }
        return match;
    }

    @Override
    public Match create(MatchCreateRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        if (request.getRound() == null) {
            throw new BusinessException(400, "轮次不能为空");
        }
        if (request.getHomeTeamId() == null || request.getAwayTeamId() == null) {
            throw new BusinessException(400, "主队/客队不能为空");
        }
        if (request.getHomeTeamId().equals(request.getAwayTeamId())) {
            throw new BusinessException(400, "主队和客队不能相同");
        }
        validateFormat(request.getFormat());

        Team home = teamService.getById(request.getHomeTeamId());
        Team away = teamService.getById(request.getAwayTeamId());
        if (home == null) throw new BusinessException(400, "主队不存在: " + request.getHomeTeamId());
        if (away == null) throw new BusinessException(400, "客队不存在: " + request.getAwayTeamId());

        String season = currentSeason;
        Integer round = request.getRound();
        String matchId = buildMatchId(season, round, home.getState(), away.getState());
        String roundLabel = (request.getRoundLabel() != null && !request.getRoundLabel().isBlank())
                ? request.getRoundLabel().trim()
                : "第 " + round + " 轮";

        Match match = new Match();
        match.setMatchId(matchId);
        match.setSeason(season);
        match.setRound(round);
        match.setRoundLabel(roundLabel);
        match.setMatchDate(parseDateTimeOrNull(request.getMatchDate()));
        match.setFormat(request.getFormat());
        match.setStatus("scheduled");
        match.setHomeTeamId(home.getId());
        match.setAwayTeamId(away.getId());
        match.setLiveUrl(blankToNull(request.getLiveUrl()));
        match.setNotes(blankToNull(request.getNotes()));
        match.setSchedulePublished(0);
        match.setResultPublished(0);

        save(match);
        return match;
    }

    @Override
    public Match update(Long id, MatchUpdateRequest request) {
        Match match = getByIdOrThrow(id);
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }

        boolean publishedResult = match.getResultPublished() != null && match.getResultPublished() == 1;
        if (publishedResult) {
            if (request.getHomeTeamId() != null && !request.getHomeTeamId().equals(match.getHomeTeamId())) {
                throw new BusinessException(400, "比赛已有已发布赛果，不允许修改主队");
            }
            if (request.getAwayTeamId() != null && !request.getAwayTeamId().equals(match.getAwayTeamId())) {
                throw new BusinessException(400, "比赛已有已发布赛果，不允许修改客队");
            }
            if (request.getFormat() != null && !request.getFormat().equals(match.getFormat())) {
                throw new BusinessException(400, "比赛已有已发布赛果，不允许修改赛制");
            }
        }

        if (request.getRound() != null) {
            match.setRound(request.getRound());
            if (request.getRoundLabel() == null || request.getRoundLabel().isBlank()) {
                match.setRoundLabel("第 " + request.getRound() + " 轮");
            }
        }
        if (request.getRoundLabel() != null && !request.getRoundLabel().isBlank()) {
            match.setRoundLabel(request.getRoundLabel().trim());
        }
        if (request.getMatchDate() != null) {
            match.setMatchDate(parseDateTimeOrNull(request.getMatchDate()));
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            match.setStatus(request.getStatus().trim());
        }
        if (request.getFormat() != null && !request.getFormat().isBlank()) {
            validateFormat(request.getFormat());
            match.setFormat(request.getFormat().trim());
        }

        Long homeTeamId = request.getHomeTeamId() != null ? request.getHomeTeamId() : match.getHomeTeamId();
        Long awayTeamId = request.getAwayTeamId() != null ? request.getAwayTeamId() : match.getAwayTeamId();
        if (homeTeamId != null && awayTeamId != null && homeTeamId.equals(awayTeamId)) {
            throw new BusinessException(400, "主队和客队不能相同");
        }
        if (request.getHomeTeamId() != null) {
            Team home = teamService.getById(request.getHomeTeamId());
            if (home == null) throw new BusinessException(400, "主队不存在: " + request.getHomeTeamId());
            match.setHomeTeamId(home.getId());
        }
        if (request.getAwayTeamId() != null) {
            Team away = teamService.getById(request.getAwayTeamId());
            if (away == null) throw new BusinessException(400, "客队不存在: " + request.getAwayTeamId());
            match.setAwayTeamId(away.getId());
        }

        match.setLiveUrl(request.getLiveUrl() != null ? blankToNull(request.getLiveUrl()) : match.getLiveUrl());
        match.setNotes(request.getNotes() != null ? blankToNull(request.getNotes()) : match.getNotes());

        // 如主客/轮次变化，则重算 matchId（保持业务唯一标识稳定且可推导）
        Team home = teamService.getById(match.getHomeTeamId());
        Team away = teamService.getById(match.getAwayTeamId());
        if (home != null && away != null) {
            match.setMatchId(buildMatchId(match.getSeason(), match.getRound(), home.getState(), away.getState()));
        }

        updateById(match);
        return match;
    }

    @Override
    public void publishSchedule(Long id) {
        Match match = getByIdOrThrow(id);

        if (match.getHomeTeamId() == null || match.getAwayTeamId() == null || match.getRound() == null || match.getFormat() == null) {
            throw new BusinessException(400, "发布校验失败：主队/客队/轮次/赛制必须完整");
        }
        if (match.getHomeTeamId().equals(match.getAwayTeamId())) {
            throw new BusinessException(400, "发布校验失败：主队和客队不能相同");
        }
        validateFormat(match.getFormat());

        match.setSchedulePublished(1);
        match.setSchedulePublishedAt(LocalDateTime.now());
        updateById(match);
    }

    @Override
    public void unpublishSchedule(Long id) {
        Match match = getByIdOrThrow(id);

        boolean publishedResult = match.getResultPublished() != null && match.getResultPublished() == 1;
        if (publishedResult) {
            throw new BusinessException(400, "比赛已有已发布赛果，不允许撤回赛程");
        }

        match.setSchedulePublished(0);
        match.setScheduleUnpublishedAt(LocalDateTime.now());
        updateById(match);
    }

    private void validateFormat(String format) {
        if (format == null || format.isBlank()) {
            throw new BusinessException(400, "赛制不能为空");
        }
        String f = format.trim().toUpperCase();
        if (f.equals("BO1") || f.equals("BO2") || f.equals("BO3")) return;
        if (f.equals("BO5") && bo5Enabled) return;
        throw new BusinessException(400, "赛制不合法: " + format);
    }

    private LocalDateTime parseDateTimeOrNull(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        try {
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "比赛时间格式不合法(需 ISO-8601 LocalDateTime): " + raw);
        }
    }

    private String buildMatchId(String season, Integer round, String homeState, String awayState) {
        String hs = slugState(homeState);
        String as = slugState(awayState);
        return String.format("%s-r%d-%s-%s", String.valueOf(season).toLowerCase(), round, hs, as);
    }

    private String slugState(String state) {
        if (state == null) return "";
        String v = state.trim();
        if (v.isEmpty()) return "";
        return v
                .replace("秦", "qin")
                .replace("楚", "chu")
                .replace("蜀", "shu")
                .replace("吴", "wu")
                .replace("越", "yue")
                .replace("燕", "yan")
                .toLowerCase();
    }

    private String blankToNull(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }
}

