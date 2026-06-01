package com.ltl.league.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Component
public class PasswordUtil {

    private static final String DEFAULT_PASSWORD = "123456";
    private static final String DEFAULT_PASSWORD_MD5 = "e10adc3949ba59abbe56e057f20f883e";

    /**
     * 加密密码（使用 MD5）
     */
    public String encryptPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "";
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 加密失败", e);
            throw new RuntimeException("密码加密失败", e);
        }
    }

    /**
     * 验证密码
     */
    public boolean verifyPassword(String inputPassword, String storedPassword) {
        if (inputPassword == null || inputPassword.isEmpty()) {
            return false;
        }

        // 如果存储的密码为空，使用默认密码验证
        if (storedPassword == null || storedPassword.isEmpty()) {
            return DEFAULT_PASSWORD.equals(inputPassword);
        }

        String encryptedInput = encryptPassword(inputPassword);
        return encryptedInput.equals(storedPassword);
    }

    /**
     * 获取默认密码的 MD5 值
     */
    public String getDefaultPasswordMd5() {
        return DEFAULT_PASSWORD_MD5;
    }

    /**
     * 检查是否是默认密码
     */
    public boolean isDefaultPassword(String password) {
        return DEFAULT_PASSWORD_MD5.equals(password);
    }
}
