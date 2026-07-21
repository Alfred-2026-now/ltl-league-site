package com.ltl.league.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.service.impl.AdminEconomyServiceImpl;
import com.ltl.league.entity.PLedger;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.Team;
import com.ltl.league.mapper.PLedgerMapper;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.mapper.TeamMapper;
import com.ltl.league.mapper.ValuationChangeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminEconomyServiceImplTest {

    @Mock
    private PLedgerMapper pLedgerMapper;

    @Mock
    private ValuationChangeMapper valuationChangeMapper;

    @Mock
    private TeamMapper teamMapper;

    @Mock
    private PlayerMapper playerMapper;

    @Mock
    private RuleParameterService ruleParameterService;

    @Mock
    private AdminAssetService adminAssetService;

    @Test
    void deductAllTeamsSalaryMatchesSalaryPaidToNonCaptainActivePlayers() {
        AdminEconomyServiceImpl service = new AdminEconomyServiceImpl(
                pLedgerMapper,
                valuationChangeMapper,
                teamMapper,
                playerMapper,
                ruleParameterService,
                adminAssetService);
        ReflectionTestUtils.setField(service, "currentSeason", "s2");

        when(ruleParameterService.getInt("salary.min_rate")).thenReturn(1);
        when(ruleParameterService.getInt("salary.max_rate")).thenReturn(100);

        Team paidTeam = team(10L, 1000);
        Team captainOnlyTeam = team(20L, 800);
        when(teamMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(paidTeam, captainOnlyTeam));

        Player member = player(1L, 10L, 0, 1001);
        Player adminMember = player(2L, 10L, 1, 2009);
        Player captain = player(3L, 10L, 2, 5000);
        Player adminCaptain = player(4L, 10L, 3, 6000);
        when(playerMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(
                        List.of(member, adminMember, captain, adminCaptain),
                        List.of(player(5L, 20L, 2, 7000)));

        service.deductAllTeamsSalary(10);

        ArgumentCaptor<PLedger> ledgerCaptor = ArgumentCaptor.forClass(PLedger.class);
        verify(pLedgerMapper).insert(ledgerCaptor.capture());
        PLedger ledger = ledgerCaptor.getValue();
        assertEquals(10L, ledger.getTeamId());
        assertEquals(-300, ledger.getAmount());
        assertEquals(1000, ledger.getBalanceBefore());
        assertEquals(700, ledger.getBalanceAfter());

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamMapper).updateById(teamCaptor.capture());
        assertEquals(10L, teamCaptor.getValue().getId());
        assertEquals(700, teamCaptor.getValue().getPCoins());
        assertEquals(800, captainOnlyTeam.getPCoins());

        verify(adminAssetService, times(1)).recordIncome(
                300,
                "team_salary_deduct",
                ledger.getReason(),
                "salary_payment",
                "p_ledger",
                ledger.getId(),
                null,
                null,
                "system");
    }

    private static Team team(Long id, Integer pCoins) {
        Team team = new Team();
        team.setId(id);
        team.setSeason("s2");
        team.setPCoins(pCoins);
        team.setDeleted(0);
        return team;
    }

    private static Player player(Long id, Long teamId, Integer role, Integer value) {
        Player player = new Player();
        player.setId(id);
        player.setTeamId(teamId);
        player.setRole(role);
        player.setValue(value);
        player.setStatus(1);
        player.setDeleted(0);
        return player;
    }
}
