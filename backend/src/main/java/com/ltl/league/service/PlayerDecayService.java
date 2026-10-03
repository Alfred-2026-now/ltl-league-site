package com.ltl.league.service;

import java.time.LocalDateTime;

/**
 * 未参赛身价衰减：选手连续未实际参加正式比赛时，按规则分阶段衰减其已激活位置的身价。
 *
 * 计时按"位置"独立：每个已激活的位置各自一个计时器，某位置身价被调整时只重置该位置的计时，
 * 其余位置照常衰减。
 */
public interface PlayerDecayService {

    /**
     * 执行一次每日衰减扫描。返回本次发生衰减的选手数。
     */
    int runDailyDecay();

    /**
     * 标记某位置发生了"有效身价变动"（视为参赛/重新起算），重置该位置的衰减计时：
     * 下次衰减日 = basedOn + 首次衰减天数，该位置衰减次数归零。
     *
     * @param player   选手实体（会被就地修改，由调用方负责持久化）
     * @param position 位置（TOP/JUG/MID/BOT/SUP）；为 null 或空时表示重置该选手全部位置
     * @param basedOn  起算基准时间（通常是改动发生时间）
     */
    void resetDecayClock(com.ltl.league.entity.Player player, String position, LocalDateTime basedOn);

    /**
     * 重置该选手全部位置的衰减计时（整人视为参赛时使用）。
     */
    default void resetDecayClock(com.ltl.league.entity.Player player, LocalDateTime basedOn) {
        resetDecayClock(player, null, basedOn);
    }
}
