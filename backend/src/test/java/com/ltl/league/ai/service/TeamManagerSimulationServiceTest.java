package com.ltl.league.ai.service;

import com.ltl.league.ai.dto.TeamManagerDtos;
import com.ltl.league.admin.service.MatchSettlementCalculator;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.SettlementRewardRule;
import com.ltl.league.entity.Team;
import com.ltl.league.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamManagerSimulationServiceTest {

    private final TeamManagerSimulationService service = new TeamManagerSimulationService(new MatchSettlementCalculator());

    @Test
    void simulateEnumeratesBo2ScorePatternsFromCurrentTeamPerspective() {
        TeamManagerDtos.SimulationRequest request = request("BO2", List.of(1L, 2L, 3L, 4L, 5L), "稳一稳");

        TeamManagerDtos.SimulationResponse response = service.simulate(
                request,
                team(1L, "秦", 5000),
                teams(),
                players(),
                rewardRules());

        assertEquals(List.of("2:0", "1:1", "0:2"),
                response.getScenarios().stream().map(TeamManagerDtos.ScenarioSimulation::getScorePattern).toList());
    }

    @Test
    void simulateCombinesRewardLuxuryTaxAndLoanFeeIntoNetChange() {
        TeamManagerDtos.SimulationRequest request = request("BO2", List.of(1L, 2L, 3L, 4L, 11L), "冲一冲");

        TeamManagerDtos.SimulationResponse response = service.simulate(
                request,
                team(1L, "秦", 5000),
                teams(),
                players(),
                rewardRules());

        TeamManagerDtos.ScenarioSimulation win = response.getScenarios().get(0);
        assertEquals("2:0", win.getScorePattern());
        assertEquals(1200, win.getMatchReward());
        assertEquals(531, win.getLuxuryTax());
        assertEquals(900, win.getLoanFeePaid());
        assertEquals(-231, win.getNetPChange());
        assertEquals(4769, win.getBalanceAfter());
    }

    @Test
    void simulateReturnsLoanRecommendationsWhenLineupIsUnderFivePlayers() {
        TeamManagerDtos.SimulationRequest request = request("BO2", List.of(1L, 2L, 3L, 4L), "保一保");

        TeamManagerDtos.SimulationResponse response = service.simulate(
                request,
                team(1L, "秦", 5000),
                teams(),
                players(),
                rewardRules());

        assertEquals(List.of(), response.getScenarios());
        assertFalse(response.getLoanRecommendations().isEmpty());
        assertEquals(15L, response.getLoanRecommendations().get(0).getPlayerId());
        assertEquals("保一保", response.getStrategy());
    }

    @Test
    void simulateReturnsReplacementLoanRecommendationsWhenLineupIsFull() {
        TeamManagerDtos.SimulationRequest request = request("BO2", List.of(1L, 2L, 3L, 4L, 5L), "冲一冲");

        TeamManagerDtos.SimulationResponse response = service.simulate(
                request,
                team(1L, "秦", 5000),
                teams(),
                players(),
                rewardRules());

        assertFalse(response.getLoanRecommendations().isEmpty());
        TeamManagerDtos.LoanRecommendation recommendation = response.getLoanRecommendations().get(0);
        assertEquals(11L, recommendation.getPlayerId());
        assertEquals("燕冲", recommendation.getPlayerName());
        assertEquals(1L, recommendation.getReplacedPlayerId());
        assertEquals("秦一", recommendation.getReplacedPlayerName());
        assertEquals(5, recommendation.getLineupPlayerIdsAfterLoan().size());
    }

    @Test
    void simulateReturnsRecommendationsForAllStrategiesWithoutUserSelectingOne() {
        TeamManagerDtos.SimulationRequest request = request("BO2", List.of(1L, 2L, 3L, 4L, 5L), null);

        TeamManagerDtos.SimulationResponse response = service.simulate(
                request,
                team(1L, "秦", 5000),
                teams(),
                players(),
                rewardRules());

        assertEquals(List.of("冲一冲", "稳一稳", "保一保"),
                response.getStrategyRecommendations().stream()
                        .map(TeamManagerDtos.StrategyRecommendation::getStrategy)
                        .toList());
        assertTrue(response.getStrategyRecommendations().stream()
                .allMatch(group -> !group.getRecommendations().isEmpty()));
        assertTrue(response.getStrategyRecommendations().stream()
                .allMatch(group -> group.getCandidateRange() != null && !group.getCandidateRange().isBlank()));
    }

    @Test
    void simulateUsesStrategyBatchIndexesToReturnAnotherRecommendationBatch() {
        TeamManagerDtos.SimulationRequest firstRequest = request("BO2", List.of(1L, 2L, 3L, 4L, 5L), null);
        firstRequest.setRecommendationBatches(Map.of("冲一冲", 0));
        TeamManagerDtos.SimulationRequest secondRequest = request("BO2", List.of(1L, 2L, 3L, 4L, 5L), null);
        secondRequest.setRecommendationBatches(Map.of("冲一冲", 1));

        TeamManagerDtos.SimulationResponse first = service.simulate(
                firstRequest,
                team(1L, "秦", 5000),
                teams(),
                players(),
                rewardRules());
        TeamManagerDtos.SimulationResponse second = service.simulate(
                secondRequest,
                team(1L, "秦", 5000),
                teams(),
                players(),
                rewardRules());

        List<Long> firstBatchIds = first.getStrategyRecommendations().stream()
                .filter(group -> "冲一冲".equals(group.getStrategy()))
                .findFirst()
                .orElseThrow()
                .getRecommendations().stream()
                .map(TeamManagerDtos.LoanRecommendation::getPlayerId)
                .toList();
        List<Long> secondBatchIds = second.getStrategyRecommendations().stream()
                .filter(group -> "冲一冲".equals(group.getStrategy()))
                .findFirst()
                .orElseThrow()
                .getRecommendations().stream()
                .map(TeamManagerDtos.LoanRecommendation::getPlayerId)
                .toList();

        assertFalse(firstBatchIds.isEmpty());
        assertFalse(secondBatchIds.isEmpty());
        assertFalse(firstBatchIds.stream().anyMatch(secondBatchIds::contains));
    }

    @Test
    void simulateUsesCanonicalBo3RewardRuleForLossPerspectiveScores() {
        TeamManagerDtos.SimulationRequest request = request("BO3", List.of(1L, 2L, 3L, 4L, 5L), "稳一稳");

        TeamManagerDtos.SimulationResponse response = service.simulate(
                request,
                team(1L, "秦", 5000),
                teams(),
                players(),
                canonicalRewardRules());

        TeamManagerDtos.ScenarioSimulation oneToTwo = response.getScenarios().stream()
                .filter(row -> "1:2".equals(row.getScorePattern()))
                .findFirst()
                .orElseThrow();
        assertEquals(800, oneToTwo.getMatchReward());
    }

    @Test
    void simulateRequiresOpponentLineupPlayers() {
        TeamManagerDtos.SimulationRequest request = request("BO2", List.of(1L, 2L, 3L, 4L, 5L), "稳一稳");
        request.setOpponentLineupPlayerIds(List.of());

        BusinessException error = assertThrows(BusinessException.class, () -> service.simulate(
                request,
                team(1L, "秦", 5000),
                teams(),
                players(),
                rewardRules()));

        assertEquals("必须选择敌方本场出场选手", error.getMessage());
    }

    private TeamManagerDtos.SimulationRequest request(String format, List<Long> lineupIds, String strategy) {
        TeamManagerDtos.SimulationRequest request = new TeamManagerDtos.SimulationRequest();
        request.setOpponentTeamId(2L);
        request.setFormat(format);
        request.setLineupPlayerIds(lineupIds);
        request.setOpponentLineupPlayerIds(List.of(6L, 7L, 8L, 9L));
        request.setStrategy(strategy);
        return request;
    }

    private List<Team> teams() {
        return List.of(
                team(1L, "秦", 5000),
                team(2L, "楚", 5000),
                team(3L, "燕", 5000));
    }

    private Team team(Long id, String state, Integer pCoins) {
        Team team = new Team();
        team.setId(id);
        team.setState(state);
        team.setName(state + "队");
        team.setPCoins(pCoins);
        return team;
    }

    private List<Player> players() {
        return List.of(
                player(1L, 1L, "秦一", 1000),
                player(2L, 1L, "秦二", 1000),
                player(3L, 1L, "秦三", 1000),
                player(4L, 1L, "秦四", 1000),
                player(5L, 1L, "秦五", 1000),
                player(6L, 2L, "楚一", 1000),
                player(7L, 2L, "楚二", 1000),
                player(8L, 2L, "楚三", 1000),
                player(9L, 2L, "楚四", 1000),
                player(10L, 3L, "燕稳", 1000),
                player(11L, 3L, "燕冲", 2000),
                player(12L, 3L, "燕冲二", 1900),
                player(13L, 3L, "燕冲三", 1800),
                player(14L, 3L, "燕稳二", 1100),
                player(15L, 3L, "燕保", 600));
    }

    private Player player(Long id, Long teamId, String name, Integer value) {
        Player player = new Player();
        player.setId(id);
        player.setTeamId(teamId);
        player.setName(name);
        player.setValue(value);
        player.setStatus(1);
        player.setDeleted(0);
        return player;
    }

    private List<SettlementRewardRule> rewardRules() {
        return List.of(
                reward("BO2", "2:0", 1200, 300, null),
                reward("BO2", "0:2", 1200, 300, null),
                reward("BO2", "1:1", null, null, 600),
                reward("BO3", "2:0", 1500, 300, null),
                reward("BO3", "0:2", 1500, 300, null),
                reward("BO3", "2:1", 1800, 800, null),
                reward("BO3", "1:2", 1800, 800, null));
    }

    private List<SettlementRewardRule> canonicalRewardRules() {
        return List.of(
                reward("BO3", "2:0", 1500, 300, null),
                reward("BO3", "2:1", 1800, 800, null));
    }

    private SettlementRewardRule reward(String format, String score, Integer winner, Integer loser, Integer draw) {
        SettlementRewardRule rule = new SettlementRewardRule();
        rule.setFormat(format);
        rule.setScorePattern(score);
        rule.setWinnerAmount(winner);
        rule.setLoserAmount(loser);
        rule.setDrawAmount(draw);
        rule.setIsActive(1);
        rule.setDeleted(0);
        return rule;
    }
}
