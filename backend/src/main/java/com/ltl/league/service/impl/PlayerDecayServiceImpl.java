package com.ltl.league.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.service.RuleParameterService;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.ValuationChange;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.mapper.ValuationChangeMapper;
import com.ltl.league.service.PlayerDecayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 未参赛身价衰减实现。
 *
 * 规则：
 *  - 位置身价被激活后开始计时，连续未参赛满"首次衰减天数"触发第一次衰减；
 *  - 首阶段：每 N 天衰减一次，比例 P1，共 K 次；后续阶段：每 M 天一次，比例 P2，持续到保底；
 *  - 每次只对"已激活"的位置身价衰减，逐位置向上取整，且不低于保底身价；
 *  - 休赛期开关打开时整体暂停。
 */
@Service
public class PlayerDecayServiceImpl implements PlayerDecayService {

    private static final Logger log = LoggerFactory.getLogger(PlayerDecayServiceImpl.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String SOURCE = "inactivity_decay";

    private final PlayerMapper playerMapper;
    private final ValuationChangeMapper valuationChangeMapper;
    private final RuleParameterService ruleParameterService;

    public PlayerDecayServiceImpl(
            PlayerMapper playerMapper,
            ValuationChangeMapper valuationChangeMapper,
            RuleParameterService ruleParameterService) {
        this.playerMapper = playerMapper;
        this.valuationChangeMapper = valuationChangeMapper;
        this.ruleParameterService = ruleParameterService;
    }

    @Override
    public void resetDecayClock(Player player, LocalDateTime basedOn) {
        if (player == null) {
            return;
        }
        int firstDays = Math.max(1, ruleParameterService.getInt("decay.first_days"));
        LocalDateTime base = basedOn != null ? basedOn : LocalDateTime.now(ZONE);
        player.setNextDecayAt(base.plusDays(firstDays));
        player.setDecayCount(0);
    }

    @Override
    @Transactional
    public int runDailyDecay() {
        if (ruleParameterService.getInt("decay.enabled") != 1) {
            log.info("未参赛衰减已关闭，跳过");
            return 0;
        }
        if (ruleParameterService.getInt("decay.pause") == 1) {
            log.info("休赛期暂停中，跳过未参赛衰减");
            return 0;
        }

        int floor = Math.max(0, ruleParameterService.getInt("decay.floor_value"));
        int stage1Times = Math.max(0, ruleParameterService.getInt("decay.stage1_times"));
        int stage1Interval = Math.max(1, ruleParameterService.getInt("decay.stage1_interval_days"));
        int stage2Interval = Math.max(1, ruleParameterService.getInt("decay.stage2_interval_days"));
        double stage1Rate = Math.max(0d, ruleParameterService.getDecimal("decay.stage1_rate"));
        double stage2Rate = Math.max(0d, ruleParameterService.getDecimal("decay.stage2_rate"));

        LocalDateTime now = LocalDateTime.now(ZONE);

        List<Player> due = playerMapper.selectList(new LambdaQueryWrapper<Player>()
                .isNotNull(Player::getNextDecayAt)
                .le(Player::getNextDecayAt, now));

        int affected = 0;
        for (Player player : due) {
            try {
                if (applyDecay(player, now, floor, stage1Times, stage1Interval, stage2Interval, stage1Rate, stage2Rate)) {
                    affected++;
                }
            } catch (Exception e) {
                log.error("选手 {} 未参赛衰减失败", player.getId(), e);
            }
        }
        if (affected > 0) {
            log.info("未参赛衰减完成，本次衰减选手数={}", affected);
        }
        return affected;
    }

    /** 对单个选手执行一次衰减；返回是否真的发生了衰减 */
    private boolean applyDecay(Player player, LocalDateTime now, int floor, int stage1Times,
            int stage1Interval, int stage2Interval, double stage1Rate, double stage2Rate) {
        int done = player.getDecayCount() != null ? player.getDecayCount() : 0;
        boolean firstStage = done < stage1Times;
        double rate = firstStage ? stage1Rate : stage2Rate;
        int interval = firstStage ? stage1Interval : stage2Interval;

        // 逐位置快照，仅处理"已激活"的位置
        String[] positions = {"TOP", "JUG", "MID", "BOT", "SUP"};
        Integer[] activations = {
                player.getTopActive(), player.getJugActive(), player.getMidActive(),
                player.getBotActive(), player.getSupActive()
        };
        Integer[] values = {
                player.getTopValue(), player.getJugValue(), player.getMidValue(),
                player.getBotValue(), player.getSupValue()
        };

        // 若一个激活位置都没有，说明该选手不参与衰减，直接跳过（不推进计时，避免"空转涨次数"）
        boolean hasActivePosition = false;
        for (Integer a : activations) {
            if (a != null && a == 1) { hasActivePosition = true; break; }
        }
        if (!hasActivePosition) {
            return false;
        }

        boolean changed = false;
        for (int i = 0; i < positions.length; i++) {
            if (activations[i] == null || activations[i] != 1) {
                continue; // 未激活的位置不衰减
            }
            int before = values[i] != null ? values[i] : 0;
            if (before <= floor) {
                continue; // 已到保底，不再下降
            }
            // 向上取整衰减，且不低于保底
            long computed = (long) Math.ceil(before * (1.0 - rate));
            int after = (int) Math.max(floor, computed);
            if (after >= before) {
                continue; // 无变化（比例过小或已触底）
            }

            ValuationChange change = new ValuationChange();
            change.setMatchId(null);
            change.setResultId(null);
            change.setPlayerId(player.getId());
            change.setPosition(positions[i]);
            change.setBeforeValue(before);
            change.setObjectiveDelta(0);
            change.setSubjectiveDelta(after - before);
            change.setSubjectiveReason("未参赛衰减（第 " + (done + 1) + " 次，比例 "
                    + formatRate(rate) + "）");
            change.setAfterValue(after);
            change.setVersion(null);
            change.setSource(SOURCE);
            change.setOperator("system");
            change.setIsVoided(0);
            change.setBeforeNextDecayAt(player.getNextDecayAt());
            change.setBeforeDecayCount(done);
            valuationChangeMapper.insert(change);

            setPositionValue(player, positions[i], after);
            values[i] = after;
            changed = true;
        }

        // 存在激活位置时，无论是否有位置实际变动（可能都已到保底），都推进计时，避免卡在原地
        player.setDecayCount(done + 1);
        player.setNextDecayAt((player.getNextDecayAt() != null ? player.getNextDecayAt() : now)
                .plusDays(interval));

        recalcMaxValueAndSync(player);
        playerMapper.updateById(player);
        return changed;
    }

    private void setPositionValue(Player player, String position, int value) {
        switch (position) {
            case "TOP": player.setTopValue(value); break;
            case "JUG": player.setJugValue(value); break;
            case "MID": player.setMidValue(value); break;
            case "BOT": player.setBotValue(value); break;
            case "SUP": player.setSupValue(value); break;
            default: break;
        }
    }

    private void recalcMaxValueAndSync(Player player) {
        int max = Math.max(
            player.getTopValue() != null ? player.getTopValue() : 0,
            Math.max(
                player.getJugValue() != null ? player.getJugValue() : 0,
                Math.max(
                    player.getMidValue() != null ? player.getMidValue() : 0,
                    Math.max(
                        player.getBotValue() != null ? player.getBotValue() : 0,
                        player.getSupValue() != null ? player.getSupValue() : 0
                    )
                )
            )
        );
        player.setMaxValue(max);
        player.setValue(max);
    }

    private static String formatRate(double rate) {
        double pct = rate * 100.0;
        if (pct == Math.rint(pct)) {
            return (int) pct + "%";
        }
        return String.format("%.1f%%", pct);
    }
}
