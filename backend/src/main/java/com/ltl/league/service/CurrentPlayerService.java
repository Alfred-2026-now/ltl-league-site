package com.ltl.league.service;

import com.ltl.league.entity.Player;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.util.AuthUtil;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class CurrentPlayerService {
    private static final int ROLE_ADMIN = 1;
    private static final Set<String> DEFAULT_ADMINS = Set.of("天下人", "陶吉吉", "大橙子");

    private final AuthUtil authUtil;
    private final PlayerMapper playerMapper;

    public CurrentPlayerService(AuthUtil authUtil, PlayerMapper playerMapper) {
        this.authUtil = authUtil;
        this.playerMapper = playerMapper;
    }

    public Player optional(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        AuthUtil.CookieData data = authUtil.parseCookieValue(token);
        if (data == null) {
            return null;
        }
        Player player = playerMapper.selectById(data.getPlayerId());
        return player != null && !Integer.valueOf(1).equals(player.getDeleted()) ? player : null;
    }

    public Player require(String token) {
        Player player = optional(token);
        if (player == null) {
            throw new BusinessException(401, "请先登录后再操作");
        }
        return player;
    }

    public Player requireAdmin(String token) {
        Player player = require(token);
        boolean explicitAdmin = player.getRole() != null && (player.getRole() & ROLE_ADMIN) != 0;
        boolean legacyDefaultAdmin = player.getRole() == null && DEFAULT_ADMINS.contains(player.getName());
        if (!explicitAdmin && !legacyDefaultAdmin) {
            throw new BusinessException(403, "需要管理员权限");
        }
        return player;
    }
}
