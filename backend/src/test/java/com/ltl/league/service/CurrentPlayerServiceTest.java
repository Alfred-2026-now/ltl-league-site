package com.ltl.league.service;

import com.ltl.league.entity.Player;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.util.AuthUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentPlayerServiceTest {
    private final AuthUtil authUtil = mock(AuthUtil.class);
    private final PlayerMapper playerMapper = mock(PlayerMapper.class);
    private final CurrentPlayerService service = new CurrentPlayerService(authUtil, playerMapper);

    @Test
    void adminCheckUsesCurrentDatabaseRoleInsteadOfCookieRole() {
        when(authUtil.parseCookieValue("token"))
                .thenReturn(new AuthUtil.CookieData(7L, "伪管理员", 1, System.currentTimeMillis()));
        Player player = new Player();
        player.setId(7L);
        player.setRole(0);
        player.setDeleted(0);
        when(playerMapper.selectById(7L)).thenReturn(player);

        BusinessException error = assertThrows(BusinessException.class, () -> service.requireAdmin("token"));

        assertEquals(403, error.getCode());
    }

    @Test
    void legacyDefaultAdminRemainsCompatibleWhenDatabaseRoleIsNull() {
        when(authUtil.parseCookieValue("token"))
                .thenReturn(new AuthUtil.CookieData(8L, "大橙子", 0, System.currentTimeMillis()));
        Player player = new Player();
        player.setId(8L);
        player.setName("大橙子");
        player.setRole(null);
        player.setDeleted(0);
        when(playerMapper.selectById(8L)).thenReturn(player);

        Player result = service.requireAdmin("token");

        assertEquals(8L, result.getId());
    }
}
