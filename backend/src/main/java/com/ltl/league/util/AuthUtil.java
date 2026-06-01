package com.ltl.league.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class AuthUtil {

    @Value("${ltl.league.auth.secret:ltl-league-auth-secret-2024}")
    private String secret;

    private static final String SEPARATOR = "|";
    private static final int COOKIE_MAX_AGE = 30 * 24 * 60 * 60; // 30天，单位秒

    /**
     * 生成认证Cookie值
     * 格式: playerId|playerName|role|timestamp|signature
     */
    public String generateCookieValue(Long playerId, String playerName, Integer role) {
        long timestamp = System.currentTimeMillis();
        String data = playerId + SEPARATOR + playerName + SEPARATOR + role + SEPARATOR + timestamp;
        String signature = generateSignature(data);

        return Base64.getEncoder().encodeToString((data + SEPARATOR + signature).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解析Cookie值
     */
    public CookieData parseCookieValue(String cookieValue) {
        try {
            String decoded = new String(Base64.getDecoder().decode(cookieValue), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|");

            if (parts.length != 5) {
                log.warn("Cookie格式错误，部分数量: {}", parts.length);
                return null;
            }

            String data = parts[0] + SEPARATOR + parts[1] + SEPARATOR + parts[2] + SEPARATOR + parts[3];
            String providedSignature = parts[4];
            String expectedSignature = generateSignature(data);

            if (!expectedSignature.equals(providedSignature)) {
                log.warn("Cookie签名验证失败");
                return null;
            }

            Long playerId = Long.valueOf(parts[0]);
            String playerName = parts[1];
            Integer role = Integer.valueOf(parts[2]);
            long timestamp = Long.valueOf(parts[3]);

            // 检查是否过期（30天）
            long currentTime = System.currentTimeMillis();
            long maxAge = COOKIE_MAX_AGE * 1000L;
            if (currentTime - timestamp > maxAge) {
                log.warn("Cookie已过期");
                return null;
            }

            return new CookieData(playerId, playerName, role, timestamp);
        } catch (Exception e) {
            log.error("解析Cookie失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 生成签名
     */
    private String generateSignature(String data) {
        String toSign = data + secret;
        return Integer.toHexString(toSign.hashCode());
    }

    /**
     * 获取Cookie最大有效期
     */
    public int getCookieMaxAge() {
        return COOKIE_MAX_AGE;
    }

    /**
     * Cookie数据内部类
     */
    public static class CookieData {
        private final Long playerId;
        private final String playerName;
        private final Integer role;
        private final Long timestamp;

        public CookieData(Long playerId, String playerName, Integer role, Long timestamp) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.role = role;
            this.timestamp = timestamp;
        }

        public Long getPlayerId() {
            return playerId;
        }

        public String getPlayerName() {
            return playerName;
        }

        public Integer getRole() {
            return role;
        }

        public Long getTimestamp() {
            return timestamp;
        }
    }
}
