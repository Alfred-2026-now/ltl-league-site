package com.ltl.league.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ltl.league.dto.GameVO;
import com.ltl.league.dto.MatchVO;
import com.ltl.league.entity.*;
import com.ltl.league.mapper.*;
import com.ltl.league.service.MatchService;
import com.ltl.league.service.TeamService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchServiceImpl extends ServiceImpl<MatchMapper, Match> implements MatchService {

    private final GameMapper gameMapper;
    private final PLedgerMapper pLedgerMapper;
    private final ValuationChangeMapper valuationChangeMapper;
    private final AttachmentMapper attachmentMapper;
    private final TeamService teamService;
    private final PlayerMapper playerMapper;
    private final GameParticipantMapper gameParticipantMapper;

    public MatchServiceImpl(
            GameMapper gameMapper,
            PLedgerMapper pLedgerMapper,
            ValuationChangeMapper valuationChangeMapper,
            AttachmentMapper attachmentMapper,
            TeamService teamService,
            PlayerMapper playerMapper,
            GameParticipantMapper gameParticipantMapper) {
        this.gameMapper = gameMapper;
        this.pLedgerMapper = pLedgerMapper;
        this.valuationChangeMapper = valuationChangeMapper;
        this.attachmentMapper = attachmentMapper;
        this.teamService = teamService;
        this.playerMapper = playerMapper;
        this.gameParticipantMapper = gameParticipantMapper;
    }

    @Override
    public List<Match> getAllMatches() {
        return lambdaQuery()
                .orderByAsc(Match::getMatchDate)
                .list();
    }

    @Override
    public Match getMatchById(Long id) {
        return getById(id);
    }

    @Override
    public List<MatchVO> getAllMatchVOs() {
        List<Match> matches = getAllMatches();
        return matches.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public MatchVO getMatchVOById(Long id) {
        Match match = getById(id);
        return match != null ? convertToVO(match) : null;
    }

    private MatchVO convertToVO(Match match) {
        MatchVO vo = new MatchVO();
        vo.setId(match.getId());
        vo.setMatchId(match.getMatchId());
        vo.setSeason(match.getSeason());
        vo.setRound(match.getRound());
        vo.setRoundLabel(match.getRoundLabel());
        vo.setMatchDate(match.getMatchDate() != null ? match.getMatchDate().toString() : null);
        vo.setFormat(match.getFormat());
        vo.setStatus(match.getStatus());
        vo.setSource(match.getSource());
        vo.setVersion(match.getVersion());
        vo.setNotes(match.getNotes());

        Team homeTeam = teamService.getById(match.getHomeTeamId());
        Team awayTeam = teamService.getById(match.getAwayTeamId());
        vo.setHomeTeam(homeTeam != null ? homeTeam.getState() : "");
        vo.setAwayTeam(awayTeam != null ? awayTeam.getState() : "");

        if (match.getHomeScore() != null && match.getAwayScore() != null) {
            MatchVO.Score score = new MatchVO.Score();
            score.setHome(match.getHomeScore());
            score.setAway(match.getAwayScore());
            vo.setScore(score);
        }

        if (match.getLiveUrl() != null) {
            MatchVO.Live live = new MatchVO.Live();
            live.setUrl(match.getLiveUrl());
            live.setLabel("正在直播");
            vo.setLive(live);
        }

        List<Game> games = gameMapper.selectList(
                new LambdaQueryWrapper<Game>()
                        .eq(Game::getMatchId, match.getId())
                        .orderByAsc(Game::getGameIndex)
        );

        List<Long> gameIds = games.stream().map(Game::getId).collect(Collectors.toList());
        List<GameParticipant> participants = gameIds.isEmpty() ? Collections.emptyList()
                : gameParticipantMapper.selectList(
                        new LambdaQueryWrapper<GameParticipant>()
                                .in(GameParticipant::getGameId, gameIds)
                                .eq(GameParticipant::getDeleted, 0)
                );

        Map<Long, List<GameParticipant>> participantsByGame = participants.stream()
                .collect(Collectors.groupingBy(GameParticipant::getGameId));

        List<GameVO> gameVOs = games.stream().map(game -> {
            GameVO gameVO = new GameVO();
            gameVO.setId(game.getId());
            gameVO.setMatchId(game.getMatchId());
            gameVO.setIndex(game.getGameIndex());
            gameVO.setWinner(game.getWinner());
            gameVO.setBlueTeam(game.getBlueTeam());
            gameVO.setRedTeam(game.getRedTeam());
            gameVO.setHomeTeam(game.getHomeTeam());
            gameVO.setAwayTeam(game.getAwayTeam());
            gameVO.setDurationSeconds(game.getDurationSeconds());
            gameVO.setSourceGameId(game.getSourceGameId());
            gameVO.setGameVersion(game.getGameVersion());

            List<GameParticipant> gameParticipants = participantsByGame.getOrDefault(game.getId(), Collections.emptyList());
            GameVO.Lineups lineups = new GameVO.Lineups();

            List<GameVO.Participant> homeParticipants = gameParticipants.stream()
                    .filter(p -> p.getTeamId().equals(match.getHomeTeamId()))
                    .map(this::convertToParticipant)
                    .collect(Collectors.toList());

            List<GameVO.Participant> awayParticipants = gameParticipants.stream()
                    .filter(p -> p.getTeamId().equals(match.getAwayTeamId()))
                    .map(this::convertToParticipant)
                    .collect(Collectors.toList());

            lineups.setHome(homeParticipants);
            lineups.setAway(awayParticipants);
            gameVO.setLineups(lineups);

            return gameVO;
        }).collect(Collectors.toList());

        vo.setGames(gameVOs);

        Map<Long, String> teamIdToState = new HashMap<>();
        if (homeTeam != null) teamIdToState.put(homeTeam.getId(), homeTeam.getState());
        if (awayTeam != null) teamIdToState.put(awayTeam.getId(), awayTeam.getState());

        List<PLedger> pLedgerList = pLedgerMapper.selectList(
                new LambdaQueryWrapper<PLedger>()
                        .eq(PLedger::getMatchId, match.getId())
                        .eq(PLedger::getIsVoided, 0)
        );
        List<MatchVO.PLedgerVO> pLedgerVOs = pLedgerList.stream().map(pl -> {
            MatchVO.PLedgerVO plVo = new MatchVO.PLedgerVO();
            String teamState = teamIdToState.get(pl.getTeamId());
            plVo.setTeam(teamState != null ? teamState : "");
            plVo.setType(pl.getType());
            plVo.setAmount(pl.getAmount());
            plVo.setReason(pl.getReason());
            return plVo;
        }).collect(Collectors.toList());
        vo.setPLedger(pLedgerVOs);

        List<ValuationChange> valuationChanges = valuationChangeMapper.selectList(
                new LambdaQueryWrapper<ValuationChange>()
                        .eq(ValuationChange::getMatchId, match.getId())
                        .eq(ValuationChange::getIsVoided, 0)
        );
        List<MatchVO.ValuationChangeVO> valuationChangeVOs = valuationChanges.stream().map(vc -> {
            MatchVO.ValuationChangeVO vcVo = new MatchVO.ValuationChangeVO();
            Player player = playerMapper.selectById(vc.getPlayerId());
            vcVo.setPlayerName(player != null ? player.getName() : "");
            vcVo.setBefore(vc.getBeforeValue());
            vcVo.setObjective(vc.getObjectiveDelta());
            vcVo.setSubjective(vc.getSubjectiveDelta());
            vcVo.setReason(vc.getSubjectiveReason());
            vcVo.setAfter(vc.getAfterValue());
            return vcVo;
        }).collect(Collectors.toList());
        vo.setValuationChanges(valuationChangeVOs);

        List<Attachment> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<Attachment>()
                        .eq(Attachment::getMatchId, match.getId())
        );
        List<MatchVO.AttachmentVO> attachmentVOs = attachments.stream().map(att -> {
            MatchVO.AttachmentVO attVo = new MatchVO.AttachmentVO();
            attVo.setLabel(att.getLabel());
            attVo.setUrl(att.getUrl());
            return attVo;
        }).collect(Collectors.toList());
        vo.setAttachments(attachmentVOs);

        return vo;
    }

    private GameVO.Participant convertToParticipant(GameParticipant p) {
        GameVO.Participant participant = new GameVO.Participant();

        Player player = playerMapper.selectById(p.getPlayerId());
        GameVO.MappedPlayer mappedPlayer = new GameVO.MappedPlayer();
        mappedPlayer.setPlayerName(player != null ? player.getName() : "");
        participant.setMappedPlayer(mappedPlayer);

        participant.setPosition(p.getPosition());

        Team currentTeam = teamService.getById(p.getTeamId());
        Team sourceTeam = p.getSourceTeamId() != null ? teamService.getById(p.getSourceTeamId()) : null;
        GameVO.RosterContext rosterContext = new GameVO.RosterContext();
        rosterContext.setIsLoan(p.getIsLoan() == 1);
        rosterContext.setIsSubstitute(p.getIsSubstitute() == 1);
        rosterContext.setRepresentingTeam(currentTeam != null ? currentTeam.getState() : "");
        rosterContext.setSourceTeam(sourceTeam != null ? sourceTeam.getState() : "");
        participant.setRosterContext(rosterContext);

        GameVO.Loadout loadout = new GameVO.Loadout();
        GameVO.Champion champion = new GameVO.Champion();
        champion.setName(p.getChampion());
        loadout.setChampion(champion);
        participant.setLoadout(loadout);

        GameVO.CombatStats combatStats = new GameVO.CombatStats();
        combatStats.setKills(p.getKills());
        combatStats.setDeaths(p.getDeaths());
        combatStats.setAssists(p.getAssists());
        combatStats.setDamageDealtToChampions(p.getDamageDealt());
        combatStats.setTotalDamageTaken(p.getDamageTaken());
        participant.setCombatStats(combatStats);

        GameVO.EconomyStats economyStats = new GameVO.EconomyStats();
        economyStats.setTotalMinionsKilled(p.getCs());
        economyStats.setGoldEarned(p.getGoldEarned());
        participant.setEconomyStats(economyStats);

        GameVO.VisionStats visionStats = new GameVO.VisionStats();
        visionStats.setVisionScore(p.getVisionScore());
        participant.setVisionStats(visionStats);

        GameVO.DerivedStats derivedStats = new GameVO.DerivedStats();
        derivedStats.setKillParticipation(p.getKillParticipation());
        participant.setDerivedStats(derivedStats);

        return participant;
    }
}
