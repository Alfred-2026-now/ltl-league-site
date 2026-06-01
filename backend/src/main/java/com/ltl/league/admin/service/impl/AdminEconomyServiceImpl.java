package com.ltl.league.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.dto.AdminPLedgerVO;
import com.ltl.league.admin.dto.AdminValuationChangeVO;
import com.ltl.league.admin.dto.DeductTeamPCoinsRequest;
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

    @Override
    @Transactional
    public void deductTeamPCoins(DeductTeamPCoinsRequest request) {
        if (request == null || request.getTeamId() == null) {
            throw new BusinessException(400, "请选择队伍");
        }
        if (request.getAmount() == null) {
            throw new BusinessException(400, "请填写扣除金额");
        }
        if (request.getAmount() <= 0) {
            throw new BusinessException(400, "扣除金额必须大于0");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(400, "请填写扣除原因");
        }

        Team team = teamMapper.selectById(request.getTeamId());
        if (team == null) {
            throw new BusinessException(404, "队伍不存在");
        }

        Integer currentPCoins = team.getPCoins() != null ? team.getPCoins() : 0;
        Integer newPCoins = currentPCoins - request.getAmount();

        if (newPCoins < 0) {
            throw new BusinessException(400, "队伍P币余额不足，当前余额：" + currentPCoins + "P，尝试扣除：" + request.getAmount() + "P");
        }

        // 创建流水记录
        PLedger ledger = new PLedger();
        ledger.setTeamId(team.getId());
        ledger.setMatchId(null);
        ledger.setResultId(null);
        ledger.setType("salary_deduct");
        ledger.setAmount(-request.getAmount());  // 负数表示扣除
        ledger.setReason(request.getReason().trim() + " [批次:" + System.currentTimeMillis() + "]");
        ledger.setVersion(null);
        ledger.setSource("salary_payment");
        ledger.setBalanceBefore(currentPCoins);
        ledger.setBalanceAfter(newPCoins);
        ledger.setIsVoided(0);
        pLedgerMapper.insert(ledger);

        // 更新队伍P币
        team.setPCoins(newPCoins);
        teamMapper.updateById(team);
    }

    @Override
    @Transactional
    public void deductAllTeamsSalary(Integer rate) {
        if (rate == null || rate < 1 || rate > 100) {
            throw new BusinessException(400, "工资比例必须在1-100之间");
        }

        // 查询所有队伍
        List<Team> teams = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                .eq(Team::getDeleted, 0));

        if (teams.isEmpty()) {
            throw new BusinessException(400, "没有队伍可以扣除工资");
        }

        // 生成批次ID
        String salaryBatchId = "TEAM_SALARY_" + System.currentTimeMillis();

        // 为每个队伍计算工资并扣除
        for (Team team : teams) {
            // 查询该队伍的所有选手
            List<Player> players = playerMapper.selectList(new LambdaQueryWrapper<Player>()
                    .eq(Player::getTeamId, team.getId())
                    .eq(Player::getDeleted, 0));

            if (players.isEmpty()) {
                continue; // 跳过没有选手的队伍
            }

            // 计算队伍选手总身价
            Integer totalPlayerValue = players.stream()
                    .mapToInt(p -> p.getValue() != null ? p.getValue() : 0)
                    .sum();

            // 计算扣除金额
            Integer deductAmount = totalPlayerValue * rate / 100;

            if (deductAmount <= 0) {
                continue; // 跳过扣除金额为0的队伍
            }

            Integer currentPCoins = team.getPCoins() != null ? team.getPCoins() : 0;
            Integer newPCoins = currentPCoins - deductAmount;

            // 即使余额不足也要扣除（允许负数）
            // 创建流水记录
            PLedger ledger = new PLedger();
            ledger.setTeamId(team.getId());
            ledger.setMatchId(null);
            ledger.setResultId(null);
            ledger.setType("salary_deduct");
            ledger.setAmount(-deductAmount);
            ledger.setReason("扣除队伍工资 [" + salaryBatchId + "]");
            ledger.setVersion(null);
            ledger.setSource("salary_payment");
            ledger.setBalanceBefore(currentPCoins);
            ledger.setBalanceAfter(newPCoins);
            ledger.setIsVoided(0);
            pLedgerMapper.insert(ledger);

            // 更新队伍P币
            team.setPCoins(newPCoins);
            teamMapper.updateById(team);
        }
    }

    @Override
    @Transactional
    public void voidDeductAllTeamsSalary(String reason) {
        // 查找最近的工资扣除批次
        List<PLedger> recentSalaryLedgers = pLedgerMapper.selectList(
                new LambdaQueryWrapper<PLedger>()
                        .eq(PLedger::getType, "salary_deduct")
                        .eq(PLedger::getDeleted, 0)
                        .eq(PLedger::getIsVoided, 0)
                        .orderByDesc(PLedger::getCreatedAt)
                        .last("LIMIT 200")
        );

        if (recentSalaryLedgers.isEmpty()) {
            throw new BusinessException(404, "未找到可撤回的工资扣除流水");
        }

        // 取最早的流水作为批次起始
        PLedger firstLedger = recentSalaryLedgers.get(0);
        String batchIdentifier = extractBatchId(firstLedger.getReason());

        if (batchIdentifier == null) {
            throw new BusinessException(400, "无法识别批次信息");
        }

        // 找出该批次的所有流水
        List<PLedger> batchLedgers = recentSalaryLedgers.stream()
                .filter(ledger -> {
                    String ledgerBatchId = extractBatchId(ledger.getReason());
                    return ledgerBatchId != null && ledgerBatchId.equals(batchIdentifier);
                })
                .collect(Collectors.toList());

        // 撤回该批次的所有工资扣除流水
        for (PLedger salaryLedger : batchLedgers) {
            // 标记为撤回
            salaryLedger.setIsVoided(1);
            pLedgerMapper.updateById(salaryLedger);

            // 恢复队伍P币（加上扣除的金额）
            Team team = teamMapper.selectById(salaryLedger.getTeamId());
            if (team != null) {
                Integer currentBalance = team.getPCoins() != null ? team.getPCoins() : 0;
                Integer deductedAmount = Math.abs(salaryLedger.getAmount());
                team.setPCoins(currentBalance + deductedAmount);
                teamMapper.updateById(team);
            }
        }
    }

    // 辅助方法：从reason中提取批次ID
    private String extractBatchId(String reason) {
        if (reason == null || !reason.contains("[")) {
            return null;
        }
        int start = reason.lastIndexOf("[");
        int end = reason.lastIndexOf("]");
        if (start > 0 && end > start) {
            return reason.substring(start + 1, end);
        }
        return null;
    }
}
