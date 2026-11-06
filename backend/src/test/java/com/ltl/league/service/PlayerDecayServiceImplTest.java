package com.ltl.league.service;

import com.ltl.league.admin.service.RuleParameterService;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.ValuationChange;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.mapper.ValuationChangeMapper;
import com.ltl.league.service.impl.PlayerDecayServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlayerDecayServiceImplTest {
    @Test
    void decayLeavesNegativeAndBelowFloorValuesUnchanged() {
        PlayerMapper players = mock(PlayerMapper.class);
        ValuationChangeMapper changes = mock(ValuationChangeMapper.class);
        PlayerDecayServiceImpl service = new PlayerDecayServiceImpl(
                players, changes, mock(RuleParameterService.class));
        Player player = new Player();
        player.setId(1L);
        player.setTopValue(-100);
        player.setJugValue(0);
        player.setMidValue(500);
        player.setBotValue(1000);
        player.setSupValue(-200);
        player.setTopActive(1);
        player.setJugActive(1);
        player.setMidActive(1);
        player.setBotActive(1);
        player.setSupActive(1);
        LocalDateTime now = LocalDateTime.of(2026, 11, 6, 12, 0);
        for (String position : new String[]{"TOP", "JUG", "MID", "BOT", "SUP"}) {
            player.setDecayAtFor(position, now.minusDays(1));
        }

        Boolean affected = ReflectionTestUtils.invokeMethod(service, "applyDecay",
                player, now, 500, 3, 7, 14, 0.1d, 0.05d);

        assertEquals(Boolean.TRUE, affected);
        assertEquals(-100, player.getTopValue());
        assertEquals(0, player.getJugValue());
        assertEquals(500, player.getMidValue());
        assertEquals(900, player.getBotValue());
        assertEquals(-200, player.getSupValue());
        assertEquals(900, player.getValue());
        ArgumentCaptor<ValuationChange> captor = ArgumentCaptor.forClass(ValuationChange.class);
        verify(changes).insert(captor.capture());
        assertEquals("BOT", captor.getValue().getPosition());
        verify(players).updateById(player);
    }
}
