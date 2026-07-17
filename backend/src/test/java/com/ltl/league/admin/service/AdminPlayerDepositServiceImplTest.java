package com.ltl.league.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.dto.AdjustPlayerDepositRequest;
import com.ltl.league.admin.dto.SalaryRequest;
import com.ltl.league.admin.dto.UpdatePlayerRequest;
import com.ltl.league.admin.service.impl.AdminPlayerDepositServiceImpl;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.PlayerDepositLedger;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.PlayerDepositLedgerMapper;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.mapper.TeamMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Test
    void paySalaryPaysOnlyNonCaptainActivePlayers() {
        AdminPlayerDepositServiceImpl service = new AdminPlayerDepositServiceImpl(
                playerMapper,
                depositLedgerMapper,
                teamMapper,
                ruleParameterService);

        when(ruleParameterService.getInt("salary.min_rate")).thenReturn(1);
        when(ruleParameterService.getInt("salary.max_rate")).thenReturn(100);

        Player member = activePlayer(1L, 0, 1000, 100);
        Player captain = activePlayer(2L, 2, 2000, 200);
        Player adminCaptain = activePlayer(3L, 3, 3000, 300);
        Player adminOnly = activePlayer(4L, 1, 4000, 400);
        when(playerMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(member, captain, adminCaptain, adminOnly));

        SalaryRequest request = new SalaryRequest();
        request.setRate(10);
        service.paySalary(request);

        ArgumentCaptor<PlayerDepositLedger> ledgerCaptor = ArgumentCaptor.forClass(PlayerDepositLedger.class);
        verify(depositLedgerMapper, times(2)).insert(ledgerCaptor.capture());
        List<PlayerDepositLedger> ledgers = ledgerCaptor.getAllValues();
        assertEquals(2, ledgers.size());
        assertTrue(ledgers.stream().anyMatch(l -> l.getPlayerId().equals(1L) && l.getAmount().equals(100)));
        assertTrue(ledgers.stream().anyMatch(l -> l.getPlayerId().equals(4L) && l.getAmount().equals(400)));
        assertTrue(ledgers.stream().noneMatch(l -> l.getPlayerId().equals(2L)));
        assertTrue(ledgers.stream().noneMatch(l -> l.getPlayerId().equals(3L)));

        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerMapper, times(2)).updateById(playerCaptor.capture());
        List<Player> updated = playerCaptor.getAllValues();
        assertTrue(updated.stream().anyMatch(p -> p.getId().equals(1L) && p.getDeposit().equals(200)));
        assertTrue(updated.stream().anyMatch(p -> p.getId().equals(4L) && p.getDeposit().equals(800)));
    }

    @Test
    void paySalaryThrowsWhenOnlyCaptainsRemain() {
        AdminPlayerDepositServiceImpl service = new AdminPlayerDepositServiceImpl(
                playerMapper,
                depositLedgerMapper,
                teamMapper,
                ruleParameterService);

        when(ruleParameterService.getInt("salary.min_rate")).thenReturn(1);
        when(ruleParameterService.getInt("salary.max_rate")).thenReturn(100);
        when(playerMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(activePlayer(2L, 2, 2000, 0), activePlayer(3L, 3, 3000, 0)));

        SalaryRequest request = new SalaryRequest();
        request.setRate(10);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.paySalary(request));
        assertEquals(400, ex.getCode());
        verify(depositLedgerMapper, never()).insert(any());
        verify(playerMapper, never()).updateById(any());
    }

    private static Player activePlayer(Long id, Integer role, Integer value, Integer deposit) {
        Player player = new Player();
        player.setId(id);
        player.setRole(role);
        player.setValue(value);
        player.setDeposit(deposit);
        player.setStatus(1);
        player.setTeamId(10L);
        player.setDeleted(0);
        return player;
    }
}
