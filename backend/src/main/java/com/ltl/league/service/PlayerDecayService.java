package com.ltl.league.service;

import java.time.LocalDateTime;

/**
 * 未参赛身价衰减：选手连续未实际参加正式比赛时，按规则分阶段衰减其已激活位置的身价。
 */
public interface PlayerDecayService {

    /**
     * 执行一次每日衰减扫描。返回本次被衰减的选手数。
     */
    int runDailyDecay();

    /**
     * 标记某选手发生了一次"有效身价变动"（视为参赛/重新起算），
     * 重置其衰减计时：下次衰减日 = basedOn + 首次衰减天数，衰减次数归零。
     *
     * @param player     选手实体（会被就地修改，由调用方负责持久化）
     * @param basedOn    起算基准时间（通常是改动发生时间）
     */
    void resetDecayClock(com.ltl.league.entity.Player player, LocalDateTime basedOn);
}
