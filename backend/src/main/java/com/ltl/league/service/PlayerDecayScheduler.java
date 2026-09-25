package com.ltl.league.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日 01:00（Asia/Shanghai）触发未参赛身价衰减扫描。
 * （与每日维护费 00:00 错开，避免同一时刻争抢数据库。）
 */
@Component
public class PlayerDecayScheduler {

    private static final Logger log = LoggerFactory.getLogger(PlayerDecayScheduler.class);

    private final PlayerDecayService playerDecayService;

    public PlayerDecayScheduler(PlayerDecayService playerDecayService) {
        this.playerDecayService = playerDecayService;
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Shanghai")
    public void runDailyDecay() {
        try {
            int affected = playerDecayService.runDailyDecay();
            log.info("定时未参赛衰减执行完成 affected={}", affected);
        } catch (Exception e) {
            log.error("定时未参赛衰减执行失败", e);
        }
    }
}
