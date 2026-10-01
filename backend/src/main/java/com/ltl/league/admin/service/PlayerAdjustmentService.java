package com.ltl.league.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.dto.BatchPlayerAdjustmentRequest;
import com.ltl.league.entity.*;
import com.ltl.league.mapper.*;
import com.ltl.league.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class PlayerAdjustmentService {
    private final PlayerMapper players;
    private final PlayerBountyLedgerMapper bounties;
    private final PlayerDepositLedgerMapper deposits;
    @Value("${ltl.league.current-season:s1}")
    private String season;

    public PlayerAdjustmentService(PlayerMapper players, PlayerBountyLedgerMapper bounties,
                                   PlayerDepositLedgerMapper deposits) {
        this.players = players;
        this.bounties = bounties;
        this.deposits = deposits;
    }

    @Transactional
    public List<Player> adjust(BatchPlayerAdjustmentRequest request, boolean bounty, String operator) {
        if (request == null || request.getPlayerIds() == null || request.getPlayerIds().isEmpty()
                || request.getPlayerIds().size() > 500 || request.getPlayerIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(400, "请选择1至500名有效选手");
        }
        if (new HashSet<>(request.getPlayerIds()).size() != request.getPlayerIds().size()) {
            throw new BusinessException(400, "选手不能重复选择");
        }
        if (request.getAmount() == null || request.getAmount() == 0) {
            throw new BusinessException(400, "调整金额必须是非零整数");
        }
        if (request.getReason() == null || request.getReason().isBlank() || request.getReason().trim().length() > 200) {
            throw new BusinessException(400, "请填写调整原因（最多200字）");
        }
        List<Player> targets = players.selectByIdsForUpdate(request.getPlayerIds().stream().sorted().toList());
        if (targets.size() != request.getPlayerIds().size()) throw new BusinessException(404, "部分选手不存在，请刷新后重试");
        // Validate every balance before writing; the enclosing transaction also rolls back database failures.
        for (Player player : targets) {
            Integer balance = bounty ? player.getBounty() : player.getDeposit();
            long after = (long) (balance == null ? 0 : balance) + request.getAmount();
            if (after > Integer.MAX_VALUE || after < Integer.MIN_VALUE || (bounty && after < 0)) {
                throw new BusinessException(400, player.getName() + "的调整后余额超出允许范围，整批未执行");
            }
        }
        for (Player player : targets) {
            Integer balance = bounty ? player.getBounty() : player.getDeposit();
            int before = balance == null ? 0 : balance;
            int after = before + request.getAmount();
            if (bounty) {
                PlayerBountyLedger ledger = new PlayerBountyLedger();
                ledger.setPlayerId(player.getId()); ledger.setSeason(season);
                ledger.setType("manual_adjustment"); ledger.setAmount(request.getAmount());
                ledger.setReason(request.getReason().trim()); ledger.setOperator(operator);
                ledger.setBalanceBefore(before); ledger.setBalanceAfter(after);
                bounties.insert(ledger); player.setBounty(after);
            } else {
                PlayerDepositLedger ledger = new PlayerDepositLedger();
                ledger.setPlayerId(player.getId()); ledger.setType("manual_adjustment");
                ledger.setAmount(request.getAmount()); ledger.setReason(request.getReason().trim());
                ledger.setOperator(operator); ledger.setSource("manual_admin"); ledger.setIsVoided(0);
                ledger.setBalanceBefore(before); ledger.setBalanceAfter(after);
                deposits.insert(ledger); player.setDeposit(after);
            }
            players.updateById(player);
        }
        return targets;
    }

    public List<PlayerBountyLedger> history(Long playerId) {
        return bounties.selectList(new LambdaQueryWrapper<PlayerBountyLedger>()
                .eq(playerId != null, PlayerBountyLedger::getPlayerId, playerId)
                .orderByDesc(PlayerBountyLedger::getId).last("LIMIT 200"));
    }
}
