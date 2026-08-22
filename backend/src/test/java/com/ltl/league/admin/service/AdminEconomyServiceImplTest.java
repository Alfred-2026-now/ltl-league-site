package com.ltl.league.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.service.impl.AdminEconomyServiceImpl;
import com.ltl.league.admin.dto.PopulationSubsidyRequest;
import com.ltl.league.admin.dto.PopulationSubsidyResultVO;
import com.ltl.league.entity.PLedger;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.Team;
import com.ltl.league.exception.BusinessException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void previewPopulationSubsidyExcludesCaptainRolesAndShowsEverySelectedTeam() {
        AdminEconomyServiceImpl service = service();
        Team firstTeam = team(10L, 1000);
        firstTeam.setState("京");
        firstTeam.setName("第一队");
        Team secondTeam = team(20L, 800);
        secondTeam.setState("沪");
        secondTeam.setName("第二队");
        when(teamMapper.selectBatchIds(any())).thenReturn(List.of(firstTeam, secondTeam));
        when(playerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                player(1L, 10L, 0, 1000),
                player(2L, 10L, 1, 1000),
                player(3L, 10L, 2, 1000),
                player(4L, 10L, 3, 1000),
                player(5L, 20L, 2, 1000)));

        PopulationSubsidyResultVO result = service.previewPopulationSubsidy(
                subsidyRequest(List.of(20L, 10L), 500, null));

        assertEquals(2, result.getSelectedTeamCount());
        assertEquals(1, result.getAffectedTeamCount());
        assertEquals(2, result.getEligiblePlayerCount());
        assertEquals(1000, result.getTotalAmount());
        assertEquals(10L, result.getTeams().get(0).getTeamId());
        assertEquals(2, result.getTeams().get(0).getEligiblePlayerCount());
        assertEquals(1000, result.getTeams().get(0).getSubsidyAmount());
        assertEquals(2000, result.getTeams().get(0).getBalanceAfter());
        assertEquals(0, result.getTeams().get(1).getSubsidyAmount());
        assertNotNull(result.getPreviewToken());
        assertFalse(result.getApplied());
    }

    @Test
    void applyPopulationSubsidyRecalculatesAndCreatesAuthoritativeLedgers() {
        AdminEconomyServiceImpl service = service();
        Team firstTeam = team(10L, 1000);
        Team secondTeam = team(20L, 800);
        List<Player> players = List.of(
                player(1L, 10L, 0, 1000),
                player(2L, 10L, 1, 1000),
                player(3L, 20L, 0, 1000),
                player(4L, 20L, 2, 1000));
        when(teamMapper.selectBatchIds(any())).thenReturn(List.of(firstTeam, secondTeam));
        when(teamMapper.selectByIdForUpdate(10L)).thenReturn(firstTeam);
        when(teamMapper.selectByIdForUpdate(20L)).thenReturn(secondTeam);
        when(playerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(players, players);

        PopulationSubsidyRequest request = subsidyRequest(List.of(10L, 20L), 500, null);
        PopulationSubsidyResultVO preview = service.previewPopulationSubsidy(request);
        request.setPreviewToken(preview.getPreviewToken());
        PopulationSubsidyResultVO applied = service.applyPopulationSubsidy(request);

        ArgumentCaptor<PLedger> ledgerCaptor = ArgumentCaptor.forClass(PLedger.class);
        verify(pLedgerMapper, times(2)).insert(ledgerCaptor.capture());
        List<PLedger> ledgers = ledgerCaptor.getAllValues();
        assertEquals(List.of(10L, 20L), ledgers.stream().map(PLedger::getTeamId).toList());
        assertEquals(List.of(1000, 500), ledgers.stream().map(PLedger::getAmount).toList());
        assertTrue(ledgers.stream().allMatch(row -> "population_subsidy".equals(row.getType())));
        assertTrue(ledgers.stream().allMatch(row -> "population_subsidy".equals(row.getSource())));

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamMapper, times(2)).updateById(teamCaptor.capture());
        assertEquals(List.of(2000, 1300), teamCaptor.getAllValues().stream().map(Team::getPCoins).toList());
        assertEquals(1500, applied.getTotalAmount());
        assertEquals(2, applied.getAffectedTeamCount());
        assertNotNull(applied.getBatchId());
        assertTrue(applied.getApplied());
    }

    @Test
    void applyPopulationSubsidyRejectsStalePreview() {
        AdminEconomyServiceImpl service = service();
        Team previewTeam = team(10L, 1000);
        Team changedTeam = team(10L, 1200);
        List<Player> players = List.of(player(1L, 10L, 0, 1000));
        when(teamMapper.selectBatchIds(any())).thenReturn(List.of(previewTeam));
        when(teamMapper.selectByIdForUpdate(10L)).thenReturn(changedTeam);
        when(playerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(players, players);

        PopulationSubsidyRequest request = subsidyRequest(List.of(10L), 500, null);
        request.setPreviewToken(service.previewPopulationSubsidy(request).getPreviewToken());
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.applyPopulationSubsidy(request));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("重新预览"));
    }

    @Test
    void previewPopulationSubsidyRejectsNonCurrentSeasonTeam() {
        AdminEconomyServiceImpl service = service();
        Team oldTeam = team(10L, 1000);
        oldTeam.setSeason("s1");
        when(teamMapper.selectBatchIds(any())).thenReturn(List.of(oldTeam));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.previewPopulationSubsidy(subsidyRequest(List.of(10L), 500, null)));

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("当前赛季"));
    }

    @Test
    void previewPopulationSubsidyRejectsAmountOverflow() {
        AdminEconomyServiceImpl service = service();
        Team selectedTeam = team(10L, 1000);
        when(teamMapper.selectBatchIds(any())).thenReturn(List.of(selectedTeam));
        when(playerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                player(1L, 10L, 0, 1000),
                player(2L, 10L, 0, 1000)));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.previewPopulationSubsidy(
                        subsidyRequest(List.of(10L), Integer.MAX_VALUE, null)));

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("金额过大"));
    }

    private AdminEconomyServiceImpl service() {
        AdminEconomyServiceImpl service = new AdminEconomyServiceImpl(
                pLedgerMapper,
                valuationChangeMapper,
                teamMapper,
                playerMapper,
                ruleParameterService,
                adminAssetService);
        ReflectionTestUtils.setField(service, "currentSeason", "s2");
        return service;
    }

    private static PopulationSubsidyRequest subsidyRequest(
            List<Long> teamIds,
            Integer perPlayerAmount,
            String previewToken) {
        PopulationSubsidyRequest request = new PopulationSubsidyRequest();
        request.setTeamIds(teamIds);
        request.setPerPlayerAmount(perPlayerAmount);
        request.setPreviewToken(previewToken);
        return request;
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
