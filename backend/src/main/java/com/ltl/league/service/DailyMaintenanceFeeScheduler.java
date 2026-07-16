package com.ltl.league.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日 00:00（Asia/Shanghai）触发维护费扣款。
 */
@Component
public class DailyMaintenanceFeeScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyMaintenanceFeeScheduler.class);

    private final DailyMaintenanceFeeService dailyMaintenanceFeeService;

    public DailyMaintenanceFeeScheduler(DailyMaintenanceFeeService dailyMaintenanceFeeService) {
        this.dailyMaintenanceFeeService = dailyMaintenanceFeeService;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Shanghai")
    public void runAtMidnight() {
        try {
            int charged = dailyMaintenanceFeeService.runDailyDeduction();
            log.info("定时每日维护费执行完成 charged={}", charged);
        } catch (Exception e) {
            log.error("定时每日维护费执行失败", e);
        }
    }
}
