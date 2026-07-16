package com.ltl.league.service;

/**
 * 每日维护费：按规则参数对选手个人积分自动扣款。
 */
public interface DailyMaintenanceFeeService {

    /**
     * 按当天规则执行一轮扣款。
     *
     * @return 实际扣款人数
     */
    int runDailyDeduction();
}
