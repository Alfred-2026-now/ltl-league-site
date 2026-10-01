package com.ltl.league.admin.service;

import com.ltl.league.admin.dto.BatchPlayerAdjustmentRequest;
import com.ltl.league.entity.*;
import com.ltl.league.mapper.*;
import com.ltl.league.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerAdjustmentServiceTest {
    private final PlayerMapper players = mock(PlayerMapper.class);
    private final PlayerBountyLedgerMapper bounties = mock(PlayerBountyLedgerMapper.class);
    private final PlayerDepositLedgerMapper deposits = mock(PlayerDepositLedgerMapper.class);
    private final PlayerAdjustmentService service = new PlayerAdjustmentService(players, bounties, deposits);

    private BatchPlayerAdjustmentRequest request(int amount, List<Long> ids) {
        BatchPlayerAdjustmentRequest request = new BatchPlayerAdjustmentRequest();
        request.setPlayerIds(ids); request.setAmount(amount); request.setReason("活动奖励");
        return request;
    }
    private Player player(long id, int balance) {
        Player p = new Player(); p.setId(id); p.setName("选手" + id);
        p.setBounty(balance); p.setDeposit(balance); return p;
    }
    @Test void bountyWritesBalancesAndAuditForEachPlayer() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "season", "s3");
        when(players.selectByIdsForUpdate(List.of(1L, 2L))).thenReturn(List.of(player(1, 5), player(2, 10)));
        var result = service.adjust(request(20, List.of(2L, 1L)), true, "助理");
        assertEquals(25, result.get(0).getBounty());
        assertEquals(30, result.get(1).getBounty());
        var captor = ArgumentCaptor.forClass(PlayerBountyLedger.class);
        verify(bounties, times(2)).insert(captor.capture());
        assertEquals("助理", captor.getAllValues().get(0).getOperator());
        assertEquals("活动奖励", captor.getAllValues().get(0).getReason());
        assertEquals(5, captor.getAllValues().get(0).getBalanceBefore());
        assertEquals(25, captor.getAllValues().get(0).getBalanceAfter());
        assertEquals("s3", captor.getAllValues().get(0).getSeason());
    }
    @Test void insufficientBountyRejectsWholeBatchBeforeWrites() {
        when(players.selectByIdsForUpdate(List.of(1L, 2L))).thenReturn(List.of(player(1, 50), player(2, 5)));
        assertThrows(BusinessException.class, () -> service.adjust(request(-10, List.of(1L, 2L)), true, "助理"));
        verifyNoInteractions(bounties, deposits);
        verify(players, never()).updateById(any(Player.class));
    }
    @Test void invalidAndMissingPlayersAreRejected() {
        assertThrows(BusinessException.class, () -> service.adjust(request(5, List.of(1L, 1L)), true, "助理"));
        assertThrows(BusinessException.class, () -> service.adjust(request(0, List.of(1L)), true, "助理"));
        var blank = request(5, List.of(1L)); blank.setReason(" ");
        assertThrows(BusinessException.class, () -> service.adjust(blank, true, "助理"));
        when(players.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.adjust(request(5, List.of(1L)), true, "助理"));
        verifyNoInteractions(bounties, deposits);
    }
    @Test void depositAllowsNegativeBalanceButRejectsOverflow() {
        when(players.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(player(1, 5)));
        assertEquals(-5, service.adjust(request(-10, List.of(1L)), false, "助理").get(0).getDeposit());
        verify(deposits).insert(any(PlayerDepositLedger.class));
        when(players.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(player(1, Integer.MAX_VALUE)));
        assertThrows(BusinessException.class, () -> service.adjust(request(1, List.of(1L)), false, "助理"));
        verify(deposits, times(1)).insert(any(PlayerDepositLedger.class));
    }
}
