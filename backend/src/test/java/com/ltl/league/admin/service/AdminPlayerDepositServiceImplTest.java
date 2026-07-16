package com.ltl.league.admin.service;

import com.ltl.league.admin.dto.AdjustPlayerDepositRequest;
import com.ltl.league.admin.dto.UpdatePlayerRequest;
import com.ltl.league.admin.service.impl.AdminPlayerDepositServiceImpl;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.PlayerDepositLedger;
import com.ltl.league.mapper.PlayerDepositLedgerMapper;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.mapper.TeamMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPlayerDepositServiceImplTest {

    @Mock
    private PlayerMapper playerMapper;

    @Mock
    private PlayerDepositLedgerMapper depositLedgerMapper;

    @Mock
    private TeamMapper teamMapper;

    @Mock
    private RuleParameterService ruleParameterService;

    @Test
    void adjustPlayerDepositAllowsAdminBalanceToBecomeNegative() {
        AdminPlayerDepositServiceImpl service = new AdminPlayerDepositServiceImpl(
                playerMapper,
                depositLedgerMapper,
                teamMapper,
                ruleParameterService);

        Player player = new Player();
        player.setId(7L);
        player.setDeposit(50);
        when(playerMapper.selectById(7L)).thenReturn(player);

        AdjustPlayerDepositRequest request = new AdjustPlayerDepositRequest();
        request.setPlayerId(7L);
        request.setAmount(-120);
        request.setReason("fine");

        service.adjustPlayerDeposit(request);

        ArgumentCaptor<PlayerDepositLedger> ledgerCaptor = ArgumentCaptor.forClass(PlayerDepositLedger.class);
        verify(depositLedgerMapper).insert(ledgerCaptor.capture());
        PlayerDepositLedger ledger = ledgerCaptor.getValue();
        assertEquals(50, ledger.getBalanceBefore());
        assertEquals(-70, ledger.getBalanceAfter());
        assertEquals(-120, ledger.getAmount());
        assertEquals("manual_adjustment", ledger.getType());

        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerMapper).updateById(playerCaptor.capture());
        assertEquals(-70, playerCaptor.getValue().getDeposit());
    }

    @Test
    void updatePlayerDoesNotAutoDeductTeamOnTransfer() {
        AdminPlayerDepositServiceImpl service = new AdminPlayerDepositServiceImpl(
                playerMapper,
                depositLedgerMapper,
                teamMapper,
                ruleParameterService);

        Player player = new Player();
        player.setId(9L);
        player.setName("测试选手");
        player.setTeamId(1L);
        player.setStatus(1);
        player.setValue(2000);
        when(playerMapper.selectById(9L)).thenReturn(player);

        UpdatePlayerRequest request = new UpdatePlayerRequest();
        request.setTeamId(2L);
        request.setStatus(1);

        service.updatePlayer(9L, request);

        verify(playerMapper).updateById(player);
        assertEquals(2L, player.getTeamId());
        verify(teamMapper, never()).selectById(org.mockito.ArgumentMatchers.any());
        verify(teamMapper, never()).updateById(org.mockito.ArgumentMatchers.any());
    }
}
