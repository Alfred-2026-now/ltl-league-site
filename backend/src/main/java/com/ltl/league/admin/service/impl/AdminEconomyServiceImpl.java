package com.ltl.league.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.dto.AdminPLedgerVO;
import com.ltl.league.admin.dto.AdminValuationChangeVO;
import com.ltl.league.admin.dto.ManualPLedgerRequest;
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

    @Override
    @Transactional
    public AdminPLedgerVO manualAddPLedger(ManualPLedgerRequest request) {
        if (request == null || request.getTeamId() == null || request.getAmount() == null) {
            throw new BusinessException(400, "请选择队伍并填写金额");
        }
        if (request.getAmount() == 0) {
            throw new BusinessException(400, "金额不能为 0");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(400, "请填写原因");
        }
        Team team = teamMapper.selectById(request.getTeamId());
        if (team == null) {
            throw new BusinessException(404, "队伍不存在");
        }

        PLedger lastLedger = pLedgerMapper.selectOne(new LambdaQueryWrapper<PLedger>()
                .eq(PLedger::getTeamId, team.getId())
                .eq(PLedger::getDeleted, 0)
                .eq(PLedger::getIsVoided, 0)
                .orderByDesc(PLedger::getCreatedAt)
                .last("LIMIT 1"));

        Integer balanceBefore = lastLedger != null ? lastLedger.getBalanceAfter() : 0;
        Integer balanceAfter = balanceBefore + request.getAmount();

        if (balanceAfter < 0) {
            throw new BusinessException(400, "队伍P币余额不足");
        }

        PLedger ledger = new PLedger();
        ledger.setTeamId(team.getId());
        ledger.setMatchId(null);
        ledger.setResultId(null);
        ledger.setType("manual_admin");
        ledger.setAmount(request.getAmount());
        ledger.setReason(request.getReason().trim());
        ledger.setVersion(null);
        ledger.setSource("manual_admin");
        ledger.setBalanceBefore(balanceBefore);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setIsVoided(0);
        pLedgerMapper.insert(ledger);

        team.setPCoins(balanceAfter);
        teamMapper.updateById(team);

        return toPLedgerVO(ledger, team);
    }

    @Override
    @Transactional
    public void voidPLedger(Long ledgerId, String reason) {
        if (ledgerId == null) {
            throw new BusinessException(400, "流水ID不能为空");
        }
        PLedger ledger = pLedgerMapper.selectById(ledgerId);
        if (ledger == null) {
            throw new BusinessException(404, "流水记录不存在");
        }
        if (ledger.getIsVoided() == 1) {
            throw new BusinessException(400, "该流水已被撤回");
        }

        ledger.setIsVoided(1);
        pLedgerMapper.updateById(ledger);

        Team team = teamMapper.selectById(ledger.getTeamId());
        if (team != null) {
            Integer newBalance = team.getPCoins() - ledger.getAmount();
            if (newBalance < 0) {
                newBalance = 0;
            }
            team.setPCoins(newBalance);
            teamMapper.updateById(team);
        }
    }

    @Override
    @Transactional
    public void voidValuationChange(Long changeId, String reason) {
        if (changeId == null) {
            throw new BusinessException(400, "身价变化ID不能为空");
        }
        ValuationChange change = valuationChangeMapper.selectById(changeId);
        if (change == null) {
            throw new BusinessException(404, "身价变化记录不存在");
        }
        if (change.getIsVoided() == 1) {
            throw new BusinessException(400, "该身价变化已被撤回");
        }

        // 查找该选手在该变化之后的所有未撤回记录
        List<ValuationChange> laterChanges = valuationChangeMapper.selectList(
                new LambdaQueryWrapper<ValuationChange>()
                        .eq(ValuationChange::getPlayerId, change.getPlayerId())
                        .gt(ValuationChange::getCreatedAt, change.getCreatedAt())
                        .eq(ValuationChange::getDeleted, 0)
                        .eq(ValuationChange::getIsVoided, 0)
                        .orderByAsc(ValuationChange::getCreatedAt)
        );

        // 将该变化及所有后续变化标记为撤回
        change.setIsVoided(1);
        valuationChangeMapper.updateById(change);

        for (ValuationChange laterChange : laterChanges) {
            laterChange.setIsVoided(1);
            valuationChangeMapper.updateById(laterChange);
        }

        // 将选手身价恢复到该变化之前的值
        Player player = playerMapper.selectById(change.getPlayerId());
        if (player != null) {
            player.setValue(change.getBeforeValue());
            playerMapper.updateById(player);
        }
    }
}
