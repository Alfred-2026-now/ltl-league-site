package com.ltl.league.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltl.league.admin.controller.AdminPlayerDepositController;
import com.ltl.league.admin.service.AdminPlayerDepositService;
import com.ltl.league.ai.dto.TeamManagerDtos;
import com.ltl.league.entity.Player;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.service.CurrentPlayerService;
import com.ltl.league.service.PlayerService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerBalanceVisibilityTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void publicPlayerAndTeamManagerResponsesDoNotExposeOtherPlayersBalances() throws Exception {
        Player player = new Player();
        player.setId(7L);
        player.setName("选手甲");
        player.setDeposit(-25);
        player.setBounty(12);
        PlayerService playerService = mock(PlayerService.class);
        when(playerService.getAllPlayers()).thenReturn(List.of(player));
        when(playerService.getPlayersByTeamId(3L)).thenReturn(List.of(player));

        PlayerController controller = new PlayerController(playerService);
        String publicJson = mapper.writeValueAsString(controller.getAllPlayers());
        assertFalse(publicJson.contains("deposit"));
        assertTrue(publicJson.contains("bounty"));
        assertFalse(mapper.writeValueAsString(controller.getPlayersByTeamId(3L)).contains("deposit"));

        TeamManagerDtos.ContextResponse context = new TeamManagerDtos.ContextResponse();
        context.setPlayers(List.of(player));
        String contextJson = mapper.writeValueAsString(context);
        assertFalse(contextJson.contains("deposit"));
        assertEquals(-25, context.getPlayers().get(0).getDeposit());
    }

    @Test
    void adminPlayerListAndLedgerRequireAdministrator() {
        CurrentPlayerService currentPlayer = mock(CurrentPlayerService.class);
        PlayerService playerService = mock(PlayerService.class);
        AdminPlayerDepositController controller = new AdminPlayerDepositController(
                mock(AdminPlayerDepositService.class), currentPlayer, playerService);
        doThrow(new BusinessException(403, "需要管理员权限"))
                .when(currentPlayer).requireAdmin("ordinary-cookie");
        doThrow(new BusinessException(401, "请先登录后再操作"))
                .when(currentPlayer).requireAdmin(null);

        assertThrows(BusinessException.class, () -> controller.listPlayers(null));
        assertThrows(BusinessException.class, () -> controller.listPlayers("ordinary-cookie"));
        assertThrows(BusinessException.class,
                () -> controller.listPlayerDepositLedgers(null, null, null, "ordinary-cookie"));
    }
}
