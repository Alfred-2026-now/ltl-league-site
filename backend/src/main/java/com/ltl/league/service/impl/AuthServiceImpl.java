package com.ltl.league.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.dto.LoginRequest;
import com.ltl.league.dto.LoginResponse;
import com.ltl.league.entity.Player;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.service.AuthService;
import com.ltl.league.util.AuthUtil;
import com.ltl.league.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final PlayerMapper playerMapper;
    private final AuthUtil authUtil;
    private final PasswordUtil passwordUtil;

    private static final Integer ROLE_USER = 0;
    private static final Integer ROLE_ADMIN = 1;

    private static final String[] DEFAULT_ADMINS = {"天下人", "陶吉吉", "大橙子"};

    @Override
    public LoginResponse login(LoginRequest request) {
        String playerName = request.getPlayerName();
        String password = request.getPassword();

        // 精确匹配选手名称
        LambdaQueryWrapper<Player> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Player::getName, playerName);
        Player player = playerMapper.selectOne(wrapper);

        if (player == null) {
            throw new IllegalArgumentException("选手不存在：" + playerName);
        }

        // 验证密码
        if (!passwordUtil.verifyPassword(password, player.getPassword())) {
            throw new IllegalArgumentException("密码错误");
        }

        // 确定用户角色
        Integer role = determineRole(player);
        player.setRole(role);

        log.info("选手登录成功: {} (角色: {})", playerName, role);

        return LoginResponse.builder()
                .playerId(player.getId())
                .playerName(player.getName())
                .role(role)
                .roleName(role == ROLE_ADMIN ? "管理员" : "普通用户")
                .build();
    }

    @Override
    public LoginResponse getCurrentUser(String token) {
        AuthUtil.CookieData cookieData = authUtil.parseCookieValue(token);
        if (cookieData == null) {
            return null;
        }

        Player player = playerMapper.selectById(cookieData.getPlayerId());
        if (player == null || player.getDeleted() == 1) {
            return null;
        }

        Integer role = determineRole(player);

        return LoginResponse.builder()
                .playerId(player.getId())
                .playerName(player.getName())
                .role(role)
                .roleName(role == ROLE_ADMIN ? "管理员" : "普通用户")
                .build();
    }

    @Override
    public void logout() {
        // Cookie-based 登录，登出操作由前端删除 Cookie 处理
        log.info("用户登出");
    }

    /**
     * 确定用户角色
     * 默认管理员：天下人、陶吉吉、大橙子
     */
    private Integer determineRole(Player player) {
        if (player.getRole() != null) {
            return player.getRole();
        }

        for (String adminName : DEFAULT_ADMINS) {
            if (adminName.equals(player.getName())) {
                return ROLE_ADMIN;
            }
        }

        return ROLE_USER;
    }
}
