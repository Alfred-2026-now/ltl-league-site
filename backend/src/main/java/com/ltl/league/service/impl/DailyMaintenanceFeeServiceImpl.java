package com.ltl.league.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ltl.league.admin.service.AdminAssetService;
import com.ltl.league.admin.service.RuleParameterService;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.PlayerDepositLedger;
import com.ltl.league.mapper.PlayerDepositLedgerMapper;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.service.DailyMaintenanceFeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class DailyMaintenanceFeeServiceImpl implements DailyMaintenanceFeeService {

    private static final Logger log = LoggerFactory.getLogger(DailyMaintenanceFeeServiceImpl.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String LEDGER_TYPE = "daily_maintenance_fee";
    private static final String LEDGER_SOURCE = "daily_fee";

    private final PlayerMapper playerMapper;
    private final PlayerDepositLedgerMapper depositLedgerMapper;
    private final RuleParameterService ruleParameterService;
    private final AdminAssetService adminAssetService;

    public DailyMaintenanceFeeServiceImpl(
            PlayerMapper playerMapper,
            PlayerDepositLedgerMapper depositLedgerMapper,
            RuleParameterService ruleParameterService,
            AdminAssetService adminAssetService) {
        this.playerMapper = playerMapper;
        this.depositLedgerMapper = depositLedgerMapper;
        this.ruleParameterService = ruleParameterService;
        this.adminAssetService = adminAssetService;
    }

    @Override
    @Transactional
    public int runDailyDeduction() {
        if (ruleParameterService.getInt("daily_fee.enabled") != 1) {
            log.info("每日维护费已关闭，跳过扣款");
            return 0;
        }

        int amount = ruleParameterService.getInt("daily_fee.amount");
        int minBalance = ruleParameterService.getInt("daily_fee.min_balance");
        if (amount <= 0) {
            log.info("每日维护费金额无效 amount={}，跳过扣款", amount);
            return 0;
        }

        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();

        // TableLogic 自动过滤已删除；只取 id 降低开销
        List<Player> players = playerMapper.selectList(new QueryWrapper<Player>().select("id"));
        int charged = 0;
        for (Player row : players) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (chargeOne(row.getId(), amount, minBalance, dayStart, dayEnd)) {
                charged++;
            }
        }
        log.info("每日维护费完成 date={} amount={} minBalance={} charged={}", today, amount, minBalance, charged);
        return charged;
    }

    private boolean chargeOne(Long playerId, int amount, int minBalance, LocalDateTime dayStart, LocalDateTime dayEnd) {
        if (alreadyChargedToday(playerId, dayStart, dayEnd)) {
            return false;
        }

        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null || (player.getDeleted() != null && player.getDeleted() == 1)) {
            return false;
        }
        if (alreadyChargedToday(playerId, dayStart, dayEnd)) {
            return false;
        }

        int balance = player.getDeposit() != null ? player.getDeposit() : 0;
        if (balance < minBalance) {
            return false;
        }

        int after = balance - amount;
        PlayerDepositLedger ledger = new PlayerDepositLedger();
        ledger.setPlayerId(player.getId());
        ledger.setMatchId(null);
        ledger.setResultId(null);
        ledger.setType(LEDGER_TYPE);
        ledger.setAmount(-amount);
        ledger.setReason("每日维护费 " + amount + "P（门槛 " + minBalance + "P）");
        ledger.setBalanceBefore(balance);
        ledger.setBalanceAfter(after);
        ledger.setSource(LEDGER_SOURCE);
        ledger.setOperator("system");
        ledger.setIsVoided(0);
        depositLedgerMapper.insert(ledger);

        player.setDeposit(after);
        playerMapper.updateById(player);

        adminAssetService.recordIncome(
                amount,
                LEDGER_TYPE,
                "选手 " + player.getName() + " 每日维护费",
                LEDGER_SOURCE,
                "player_deposit_ledger",
                ledger.getId(),
                null,
                null,
                "system");
        return true;
    }

    private boolean alreadyChargedToday(Long playerId, LocalDateTime dayStart, LocalDateTime dayEnd) {
        Long count = depositLedgerMapper.selectCount(new QueryWrapper<PlayerDepositLedger>()
                .eq("player_id", playerId)
                .eq("type", LEDGER_TYPE)
                .eq("is_voided", 0)
                .ge("created_at", dayStart)
                .lt("created_at", dayEnd));
        return count != null && count > 0;
    }
}
