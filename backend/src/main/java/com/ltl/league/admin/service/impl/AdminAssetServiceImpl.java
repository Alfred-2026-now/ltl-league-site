package com.ltl.league.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.dto.AssetChangeVO;
import com.ltl.league.admin.dto.AssetOverviewVO;
import com.ltl.league.admin.dto.LeagueAssetAdjustRequest;
import com.ltl.league.admin.dto.LeagueAssetLedgerVO;
import com.ltl.league.admin.service.AdminAssetService;
import com.ltl.league.entity.LeagueAssetLedger;
import com.ltl.league.entity.PLedger;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.PlayerDepositLedger;
import com.ltl.league.entity.Team;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.LeagueAssetLedgerMapper;
import com.ltl.league.mapper.PLedgerMapper;
import com.ltl.league.mapper.PlayerDepositLedgerMapper;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.mapper.TeamMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminAssetServiceImpl implements AdminAssetService {

    private final TeamMapper teamMapper;
    private final PlayerMapper playerMapper;
    private final PLedgerMapper pLedgerMapper;
    private final PlayerDepositLedgerMapper playerDepositLedgerMapper;
    private final LeagueAssetLedgerMapper leagueAssetLedgerMapper;

    public AdminAssetServiceImpl(
            TeamMapper teamMapper,
            PlayerMapper playerMapper,
            PLedgerMapper pLedgerMapper,
            PlayerDepositLedgerMapper playerDepositLedgerMapper,
            LeagueAssetLedgerMapper leagueAssetLedgerMapper) {
        this.teamMapper = teamMapper;
        this.playerMapper = playerMapper;
        this.pLedgerMapper = pLedgerMapper;
        this.playerDepositLedgerMapper = playerDepositLedgerMapper;
        this.leagueAssetLedgerMapper = leagueAssetLedgerMapper;
    }

    @Override
    public AssetOverviewVO getOverview(Integer days) {
        int normalizedDays = normalizeDays(days);
        AssetOverviewVO vo = new AssetOverviewVO();
        vo.setTeamAssets(sumTeamAssets());
        vo.setLeagueAssets(currentLeagueAssets());
        vo.setPlayerAssets(sumPlayerAssets());
        vo.setTotalAssets(vo.getTeamAssets() + vo.getLeagueAssets() + vo.getPlayerAssets());
        vo.setChanges(buildChanges(normalizedDays));
        return vo;
    }

    @Override
    public List<LeagueAssetLedgerVO> listLeagueAssetLedgers(Integer limit) {
        return leagueAssetLedgerMapper.selectList(new LambdaQueryWrapper<LeagueAssetLedger>()
                        .eq(LeagueAssetLedger::getDeleted, 0)
                        .orderByDesc(LeagueAssetLedger::getCreatedAt)
                        .last("LIMIT " + normalizeLimit(limit)))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public LeagueAssetLedgerVO manualAdjust(LeagueAssetAdjustRequest request) {
        if (request == null || request.getAmount() == null || request.getAmount() == 0) {
            throw new BusinessException(400, "请填写非 0 的联盟资产调整金额");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(400, "请填写调整原因");
        }
        LeagueAssetLedger ledger = insertLedger(
                request.getAmount(),
                request.getAmount() > 0 ? "manual_income" : "welfare_expense",
                request.getReason().trim(),
                "manual_admin",
                null,
                null,
                null,
                null,
                "admin");
        return toVO(ledger);
    }

    @Override
    @Transactional
    public void recordIncome(
            Integer amount,
            String type,
            String reason,
            String source,
            String refTable,
            Long refId,
            Long matchId,
            Long resultId,
            String operator) {
        if (amount == null || amount <= 0) {
            return;
        }
        insertLedger(amount, type, reason, source, refTable, refId, matchId, resultId, operator);
    }

    @Override
    @Transactional
    public void recordReversal(
            Integer amount,
            String type,
            String reason,
            String source,
            String refTable,
            Long refId,
            Long matchId,
            Long resultId,
            String operator) {
        if (amount == null || amount == 0) {
            return;
        }
        insertLedger(-amount, type, reason, source, refTable, refId, matchId, resultId, operator);
    }

    @Override
    @Transactional
    public void reverseMatchResult(Long resultId, String reason) {
        if (resultId == null) {
            return;
        }
        List<LeagueAssetLedger> ledgers = leagueAssetLedgerMapper.selectList(new LambdaQueryWrapper<LeagueAssetLedger>()
                .eq(LeagueAssetLedger::getResultId, resultId)
                .eq(LeagueAssetLedger::getSource, "match_result")
                .eq(LeagueAssetLedger::getDeleted, 0));
        for (LeagueAssetLedger ledger : ledgers) {
            if (ledger.getAmount() == null || ledger.getAmount() == 0) {
                continue;
            }
            insertLedger(
                    -ledger.getAmount(),
                    "match_result_reversal",
                    reason != null && !reason.isBlank() ? reason : "赛果撤回，回滚联盟资产",
                    "match_result_reversal",
                    "league_asset_ledger",
                    ledger.getId(),
                    ledger.getMatchId(),
                    ledger.getResultId(),
                    "system");
        }
    }

    private LeagueAssetLedger insertLedger(
            Integer amount,
            String type,
            String reason,
            String source,
            String refTable,
            Long refId,
            Long matchId,
            Long resultId,
            String operator) {
        Integer before = currentLeagueAssets();
        Integer after = before + amount;
        if (after < 0) {
            throw new BusinessException(400, "联盟总资产不足，当前余额：" + before + "P，尝试扣除：" + Math.abs(amount) + "P");
        }

        LeagueAssetLedger ledger = new LeagueAssetLedger();
        ledger.setType(type);
        ledger.setAmount(amount);
        ledger.setReason(reason);
        ledger.setSource(source);
        ledger.setRefTable(refTable);
        ledger.setRefId(refId);
        ledger.setMatchId(matchId);
        ledger.setResultId(resultId);
        ledger.setOperator(operator);
        ledger.setBalanceBefore(before);
        ledger.setBalanceAfter(after);
        ledger.setIsVoided(0);
        leagueAssetLedgerMapper.insert(ledger);
        return ledger;
    }

    private Integer sumTeamAssets() {
        return teamMapper.selectList(new LambdaQueryWrapper<Team>()
                        .eq(Team::getDeleted, 0))
                .stream()
                .map(Team::getPCoins)
                .mapToInt(value -> value != null ? value : 0)
                .sum();
    }

    private Integer sumPlayerAssets() {
        return playerMapper.selectList(new LambdaQueryWrapper<Player>()
                        .eq(Player::getDeleted, 0))
                .stream()
                .map(Player::getDeposit)
                .mapToInt(value -> value != null ? value : 0)
                .sum();
    }

    private Integer currentLeagueAssets() {
        LeagueAssetLedger latest = leagueAssetLedgerMapper.selectOne(new LambdaQueryWrapper<LeagueAssetLedger>()
                .eq(LeagueAssetLedger::getDeleted, 0)
                .orderByDesc(LeagueAssetLedger::getCreatedAt)
                .orderByDesc(LeagueAssetLedger::getId)
                .last("LIMIT 1"));
        return latest != null && latest.getBalanceAfter() != null ? latest.getBalanceAfter() : 0;
    }

    private List<AssetChangeVO> buildChanges(int days) {
        LocalDate startDate = LocalDate.now().minusDays(days - 1L);
        LocalDateTime startAt = startDate.atStartOfDay();
        Map<LocalDate, AssetChangeVO> rows = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            AssetChangeVO row = new AssetChangeVO();
            row.setDate(date.toString());
            row.setTeamDelta(0);
            row.setLeagueDelta(0);
            row.setPlayerDelta(0);
            row.setTotalDelta(0);
            rows.put(date, row);
        }

        pLedgerMapper.selectList(new LambdaQueryWrapper<PLedger>()
                        .ge(PLedger::getCreatedAt, startAt)
                        .eq(PLedger::getDeleted, 0)
                        .eq(PLedger::getIsVoided, 0))
                .forEach(row -> addTeamDelta(rows, row.getCreatedAt(), row.getAmount()));

        playerDepositLedgerMapper.selectList(new LambdaQueryWrapper<PlayerDepositLedger>()
                        .ge(PlayerDepositLedger::getCreatedAt, startAt)
                        .eq(PlayerDepositLedger::getDeleted, 0)
                        .eq(PlayerDepositLedger::getIsVoided, 0))
                .forEach(row -> addPlayerDelta(rows, row.getCreatedAt(), row.getAmount()));

        leagueAssetLedgerMapper.selectList(new LambdaQueryWrapper<LeagueAssetLedger>()
                        .ge(LeagueAssetLedger::getCreatedAt, startAt)
                        .eq(LeagueAssetLedger::getDeleted, 0))
                .forEach(row -> addLeagueDelta(rows, row.getCreatedAt(), row.getAmount()));

        rows.values().forEach(row -> row.setTotalDelta(row.getTeamDelta() + row.getLeagueDelta() + row.getPlayerDelta()));
        return List.copyOf(rows.values());
    }

    private void addTeamDelta(Map<LocalDate, AssetChangeVO> rows, LocalDateTime createdAt, Integer amount) {
        AssetChangeVO row = rowFor(rows, createdAt);
        if (row != null) {
            row.setTeamDelta(row.getTeamDelta() + safeAmount(amount));
        }
    }

    private void addPlayerDelta(Map<LocalDate, AssetChangeVO> rows, LocalDateTime createdAt, Integer amount) {
        AssetChangeVO row = rowFor(rows, createdAt);
        if (row != null) {
            row.setPlayerDelta(row.getPlayerDelta() + safeAmount(amount));
        }
    }

    private void addLeagueDelta(Map<LocalDate, AssetChangeVO> rows, LocalDateTime createdAt, Integer amount) {
        AssetChangeVO row = rowFor(rows, createdAt);
        if (row != null) {
            row.setLeagueDelta(row.getLeagueDelta() + safeAmount(amount));
        }
    }

    private AssetChangeVO rowFor(Map<LocalDate, AssetChangeVO> rows, LocalDateTime createdAt) {
        if (createdAt == null) {
            return null;
        }
        return rows.get(createdAt.toLocalDate());
    }

    private int safeAmount(Integer amount) {
        return amount != null ? amount : 0;
    }

    private LeagueAssetLedgerVO toVO(LeagueAssetLedger row) {
        LeagueAssetLedgerVO vo = new LeagueAssetLedgerVO();
        vo.setId(row.getId());
        vo.setType(row.getType());
        vo.setAmount(row.getAmount());
        vo.setReason(row.getReason());
        vo.setSource(row.getSource());
        vo.setRefTable(row.getRefTable());
        vo.setRefId(row.getRefId());
        vo.setMatchId(row.getMatchId());
        vo.setResultId(row.getResultId());
        vo.setOperator(row.getOperator());
        vo.setBalanceBefore(row.getBalanceBefore());
        vo.setBalanceAfter(row.getBalanceAfter());
        vo.setIsVoided(row.getIsVoided());
        vo.setCreatedAt(row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
        return vo;
    }

    private int normalizeDays(Integer days) {
        if (days == null) {
            return 14;
        }
        return Math.max(1, Math.min(days, 60));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 100;
        }
        return Math.max(1, Math.min(limit, 500));
    }
}
