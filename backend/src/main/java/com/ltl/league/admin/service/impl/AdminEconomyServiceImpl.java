package com.ltl.league.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.dto.AdminPLedgerVO;
import com.ltl.league.admin.dto.AdminValuationChangeVO;
import com.ltl.league.admin.dto.ManualValuationAdjustRequest;
import com.ltl.league.admin.service.AdminEconomyService;
import com.ltl.league.entity.PLedger;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.Team;
import com.ltl.league.entity.ValuationChange;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.PLedgerMapper;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.mapper.TeamMapper;
import com.ltl.league.mapper.ValuationChangeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminEconomyServiceImpl implements AdminEconomyService {

    private final PLedgerMapper pLedgerMapper;
    private final ValuationChangeMapper valuationChangeMapper;
    private final TeamMapper teamMapper;
    private final PlayerMapper playerMapper;

    public AdminEconomyServiceImpl(
            PLedgerMapper pLedgerMapper,
            ValuationChangeMapper valuationChangeMapper,
            TeamMapper teamMapper,
            PlayerMapper playerMapper) {
        this.pLedgerMapper = pLedgerMapper;
        this.valuationChangeMapper = valuationChangeMapper;
        this.teamMapper = teamMapper;
        this.playerMapper = playerMapper;
    }

    @Override
    public List<AdminPLedgerVO> listPLedgers(Long teamId, Long matchId, String type, Integer isVoided, String source, Integer limit) {
        List<PLedger> ledgers = pLedgerMapper.selectList(new LambdaQueryWrapper<PLedger>()
                .eq(teamId != null, PLedger::getTeamId, teamId)
                .eq(matchId != null, PLedger::getMatchId, matchId)
                .eq(type != null && !type.isBlank(), PLedger::getType, type)
                .eq(isVoided != null, PLedger::getIsVoided, isVoided)
                .eq(source != null && !source.isBlank(), PLedger::getSource, source)
                .eq(PLedger::getDeleted, 0)
                .orderByDesc(PLedger::getCreatedAt)
                .last("LIMIT " + normalizeLimit(limit)));
        Map<Long, Team> teams = loadTeams(ledgers.stream().map(PLedger::getTeamId).collect(Collectors.toSet()));
        return ledgers.stream().map(row -> toPLedgerVO(row, teams.get(row.getTeamId()))).collect(Collectors.toList());
    }

    @Override
    public List<AdminValuationChangeVO> listValuationChanges(Long playerId, Long teamId, Long matchId, String source, Integer isVoided, Integer limit) {
        List<ValuationChange> changes = valuationChangeMapper.selectList(new LambdaQueryWrapper<ValuationChange>()
                .eq(playerId != null, ValuationChange::getPlayerId, playerId)
                .eq(matchId != null, ValuationChange::getMatchId, matchId)
                .eq(source != null && !source.isBlank(), ValuationChange::getSource, source)
                .eq(isVoided != null, ValuationChange::getIsVoided, isVoided)
                .eq(ValuationChange::getDeleted, 0)
                .orderByDesc(ValuationChange::getCreatedAt)
                .last("LIMIT " + normalizeLimit(limit)));
        Map<Long, Player> players = loadPlayers(changes.stream().map(ValuationChange::getPlayerId).collect(Collectors.toSet()));
        Map<Long, Team> teams = loadTeams(players.values().stream().map(Player::getTeamId).collect(Collectors.toSet()));
        return changes.stream()
                .map(row -> toValuationVO(row, players.get(row.getPlayerId()), teams))
                .filter(vo -> teamId == null || Objects.equals(vo.getTeamId(), teamId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AdminValuationChangeVO manualAdjustment(ManualValuationAdjustRequest request) {
        if (request == null || request.getPlayerId() == null || request.getAfterValue() == null) {
            throw new BusinessException(400, "请选择选手并填写调整后身价");
        }
        if (request.getAfterValue() < 0) {
            throw new BusinessException(400, "调整后身价不能小于 0");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(400, "请填写调整原因");
        }
        Player player = playerMapper.selectById(request.getPlayerId());
        if (player == null) {
            throw new BusinessException(404, "选手不存在");
        }
        ValuationChange change = new ValuationChange();
        change.setMatchId(null);
        change.setResultId(null);
        change.setPlayerId(player.getId());
        change.setBeforeValue(player.getValue());
        change.setObjectiveDelta(0);
        change.setSubjectiveDelta(request.getAfterValue() - player.getValue());
        change.setSubjectiveReason(request.getReason().trim());
        change.setAfterValue(request.getAfterValue());
        change.setVersion(null);
        change.setSource("manual_adjustment");
        change.setOperator("admin");
        change.setIsVoided(0);
        valuationChangeMapper.insert(change);
        player.setValue(request.getAfterValue());
        playerMapper.updateById(player);
        Team team = player.getTeamId() != null ? teamMapper.selectById(player.getTeamId()) : null;
        return toValuationVO(change, player, team == null ? Collections.emptyMap() : Map.of(team.getId(), team));
    }

    private AdminPLedgerVO toPLedgerVO(PLedger row, Team team) {
        AdminPLedgerVO vo = new AdminPLedgerVO();
        vo.setId(row.getId());
        vo.setTeamId(row.getTeamId());
        vo.setTeamState(team != null ? team.getState() : "");
        vo.setTeamName(team != null ? team.getName() : "");
        vo.setMatchId(row.getMatchId());
        vo.setResultId(row.getResultId());
        vo.setType(row.getType());
        vo.setAmount(row.getAmount());
        vo.setReason(row.getReason());
        vo.setVersion(row.getVersion());
        vo.setSource(row.getSource());
        vo.setBalanceBefore(row.getBalanceBefore());
        vo.setBalanceAfter(row.getBalanceAfter());
        vo.setIsVoided(row.getIsVoided());
        vo.setCreatedAt(row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
        return vo;
    }

    private AdminValuationChangeVO toValuationVO(ValuationChange row, Player player, Map<Long, Team> teams) {
        Team team = player != null ? teams.get(player.getTeamId()) : null;
        AdminValuationChangeVO vo = new AdminValuationChangeVO();
        vo.setId(row.getId());
        vo.setMatchId(row.getMatchId());
        vo.setResultId(row.getResultId());
        vo.setPlayerId(row.getPlayerId());
        vo.setPlayerName(player != null ? player.getName() : "");
        vo.setTeamId(player != null ? player.getTeamId() : null);
        vo.setTeamState(team != null ? team.getState() : "");
        vo.setBeforeValue(row.getBeforeValue());
        vo.setObjectiveDelta(row.getObjectiveDelta());
        vo.setSubjectiveDelta(row.getSubjectiveDelta());
        vo.setSubjectiveReason(row.getSubjectiveReason());
        vo.setAfterValue(row.getAfterValue());
        vo.setVersion(row.getVersion());
        vo.setSource(row.getSource());
        vo.setIsVoided(row.getIsVoided());
        vo.setCreatedAt(row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
        return vo;
    }

    private Map<Long, Team> loadTeams(Set<Long> ids) {
        List<Long> filtered = ids.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (filtered.isEmpty()) {
            return Collections.emptyMap();
        }
        return teamMapper.selectBatchIds(filtered).stream().collect(Collectors.toMap(Team::getId, Function.identity()));
    }

    private Map<Long, Player> loadPlayers(Set<Long> ids) {
        List<Long> filtered = ids.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (filtered.isEmpty()) {
            return Collections.emptyMap();
        }
        return playerMapper.selectBatchIds(filtered).stream().collect(Collectors.toMap(Player::getId, Function.identity()));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 100;
        }
        return Math.max(1, Math.min(limit, 500));
    }
}
