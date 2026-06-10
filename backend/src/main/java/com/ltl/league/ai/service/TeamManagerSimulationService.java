package com.ltl.league.ai.service;

import com.ltl.league.ai.dto.TeamManagerDtos;
import com.ltl.league.admin.service.MatchSettlementCalculator;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.SettlementRewardRule;
import com.ltl.league.entity.Team;
import com.ltl.league.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TeamManagerSimulationService {

    private static final int STANDARD_ROSTER_SIZE = 5;
    private static final int STRATEGY_PAGE_SIZE = 3;
    private static final List<String> STRATEGIES = List.of("冲一冲", "稳一稳", "保一保");

    private final MatchSettlementCalculator calculator;

    public TeamManagerSimulationService(MatchSettlementCalculator calculator) {
        this.calculator = calculator;
    }

    public TeamManagerDtos.SimulationResponse simulate(
            TeamManagerDtos.SimulationRequest request,
            Team currentTeam,
            List<Team> teams,
            List<Player> players,
            List<SettlementRewardRule> rewardRules) {
        requireRequest(request, currentTeam);
        Map<Long, Player> playersById = players.stream()
                .filter(player -> player.getId() != null)
                .collect(Collectors.toMap(Player::getId, Function.identity(), (left, right) -> left));
        List<Player> lineup = request.getLineupPlayerIds().stream()
                .map(playersById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<Player> opponentLineup = request.getOpponentLineupPlayerIds().stream()
                .map(playersById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        int rosterSize = countRosterSize(currentTeam, players);
        double leagueStandard = calculateLeagueStandard(players);

        if (lineup.size() < STANDARD_ROSTER_SIZE) {
            TeamManagerDtos.SimulationResponse response = buildUnderfilledSimulation(request, currentTeam, lineup, opponentLineup);
            applyRecommendations(request, response, currentTeam, teams, players, rewardRules, lineup, opponentLineup, rosterSize, leagueStandard);
            response.getWarnings().add("我方出场未满 5 人，先补齐阵容后再计算本场输赢收益。");
            return response;
        }
        TeamManagerDtos.SimulationResponse response = buildSimulation(
                request, currentTeam, lineup, opponentLineup, rewardRules, rosterSize, leagueStandard);
        applyRecommendations(request, response, currentTeam, teams, players, rewardRules, lineup, opponentLineup, rosterSize, leagueStandard);
        return response;
    }

    private void applyRecommendations(
            TeamManagerDtos.SimulationRequest request,
            TeamManagerDtos.SimulationResponse response,
            Team currentTeam,
            List<Team> teams,
            List<Player> players,
            List<SettlementRewardRule> rewardRules,
            List<Player> lineup,
            List<Player> opponentLineup,
            int rosterSize,
            double leagueStandard) {
        List<TeamManagerDtos.StrategyRecommendation> groups = buildStrategyRecommendations(
                request, currentTeam, teams, players, rewardRules, lineup, opponentLineup, rosterSize, leagueStandard);
        response.setStrategyRecommendations(groups);
        String compatibilityStrategy = normalizeStrategy(request.getStrategy());
        response.setLoanRecommendations(groups.stream()
                .filter(group -> compatibilityStrategy.equals(group.getStrategy()))
                .findFirst()
                .map(TeamManagerDtos.StrategyRecommendation::getRecommendations)
                .orElseGet(List::of));
    }

    private TeamManagerDtos.SimulationResponse buildUnderfilledSimulation(
            TeamManagerDtos.SimulationRequest request,
            Team currentTeam,
            List<Player> lineup,
            List<Player> opponentLineup) {
        TeamManagerDtos.SimulationResponse response = new TeamManagerDtos.SimulationResponse();
        response.setCurrentTeamId(currentTeam.getId());
        response.setOpponentTeamId(request.getOpponentTeamId());
        response.setFormat(normalizeFormat(request.getFormat()));
        response.setStrategy(normalizeStrategy(request.getStrategy()));
        response.setLineValue(lineup.stream().map(Player::getValue).filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
        response.setRosterSize(STANDARD_ROSTER_SIZE);
        response.setLuxuryTax(0);
        response.setLoanFeePaid(0);
        response.setBalanceBefore(valueOrZero(currentTeam.getPCoins()));
        response.setOpponentLineValue(opponentLineup.stream().map(Player::getValue).filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
        response.setOpponentHasLoans(opponentLineup.stream()
                .anyMatch(player -> !Objects.equals(player.getTeamId(), request.getOpponentTeamId())));
        return response;
    }

    private TeamManagerDtos.SimulationResponse buildSimulation(
            TeamManagerDtos.SimulationRequest request,
            Team currentTeam,
            List<Player> lineup,
            List<Player> opponentLineup,
            List<SettlementRewardRule> rewardRules,
            int rosterSize,
            double leagueStandard) {
        String format = normalizeFormat(request.getFormat());
        int lineValue = lineup.stream().map(Player::getValue).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int opponentLineValue = opponentLineup.stream().map(Player::getValue).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int loanFeePaid = calculateLoanFeePaid(currentTeam, lineup, format);
        int luxuryTax = calculator.calculateLuxuryTax(leagueStandard, lineValue, rosterSize, format).tax();

        TeamManagerDtos.SimulationResponse response = new TeamManagerDtos.SimulationResponse();
        response.setCurrentTeamId(currentTeam.getId());
        response.setOpponentTeamId(request.getOpponentTeamId());
        response.setFormat(format);
        response.setStrategy(normalizeStrategy(request.getStrategy()));
        response.setLineValue(lineValue);
        response.setRosterSize(rosterSize);
        response.setLuxuryTax(luxuryTax);
        response.setLoanFeePaid(loanFeePaid);
        response.setBalanceBefore(valueOrZero(currentTeam.getPCoins()));
        response.setOpponentLineValue(opponentLineValue);
        response.setOpponentHasLoans(opponentLineup.stream()
                .anyMatch(player -> !Objects.equals(player.getTeamId(), request.getOpponentTeamId())));

        for (String score : scorePatterns(format)) {
            response.getScenarios().add(buildScenario(
                    score, format, currentTeam, rewardRules, luxuryTax, loanFeePaid));
        }
        return response;
    }

    private TeamManagerDtos.ScenarioSimulation buildScenario(
            String score,
            String format,
            Team currentTeam,
            List<SettlementRewardRule> rewardRules,
            int luxuryTax,
            int loanFeePaid) {
        int reward = calculateReward(format, score, rewardRules);
        int before = valueOrZero(currentTeam.getPCoins());
        int net = reward - luxuryTax - loanFeePaid;
        int after = before + net;

        TeamManagerDtos.ScenarioSimulation scenario = new TeamManagerDtos.ScenarioSimulation();
        scenario.setScorePattern(score);
        scenario.setMatchReward(reward);
        scenario.setLuxuryTax(luxuryTax);
        scenario.setLoanFeePaid(loanFeePaid);
        scenario.setLoanFeeReceived(0);
        scenario.setNetPChange(net);
        scenario.setBalanceBefore(before);
        scenario.setBalanceAfter(after);
        scenario.getBreakdown().add("比赛奖励 " + reward + "P");
        scenario.getBreakdown().add("奢侈税 -" + luxuryTax + "P");
        scenario.getBreakdown().add("租借费 -" + loanFeePaid + "P");
        if (after < 0) {
            scenario.getWarnings().add(currentTeam.getState() + " 赛后余额为 " + after + "P");
        }
        return scenario;
    }

    private List<TeamManagerDtos.StrategyRecommendation> buildStrategyRecommendations(
            TeamManagerDtos.SimulationRequest request,
            Team currentTeam,
            List<Team> teams,
            List<Player> players,
            List<SettlementRewardRule> rewardRules,
            List<Player> lineup,
            List<Player> opponentLineup,
            int rosterSize,
            double leagueStandard) {
        return STRATEGIES.parallelStream()
                .map(strategy -> buildStrategyRecommendation(
                        request, currentTeam, teams, players, rewardRules, lineup, opponentLineup, rosterSize, leagueStandard, strategy))
                .collect(Collectors.toList());
    }

    private TeamManagerDtos.StrategyRecommendation buildStrategyRecommendation(
            TeamManagerDtos.SimulationRequest request,
            Team currentTeam,
            List<Team> teams,
            List<Player> players,
            List<SettlementRewardRule> rewardRules,
            List<Player> lineup,
            List<Player> opponentLineup,
            int rosterSize,
            double leagueStandard,
            String strategy) {
        Set<Long> selectedIds = lineup.stream().map(Player::getId).collect(Collectors.toSet());
        String format = normalizeFormat(request.getFormat());
        Map<Long, Team> teamsById = teams.stream()
                .filter(team -> team.getId() != null)
                .collect(Collectors.toMap(Team::getId, Function.identity(), (left, right) -> left));
        int batchIndex = batchIndex(request, strategy);
        List<Player> candidates = players.stream()
                .filter(player -> player.getId() != null)
                .filter(player -> !selectedIds.contains(player.getId()))
                .filter(player -> player.getStatus() == null || player.getStatus() == 1)
                .filter(player -> !Objects.equals(player.getTeamId(), currentTeam.getId()))
                .filter(player -> !Objects.equals(player.getTeamId(), request.getOpponentTeamId()))
                .sorted(candidateComparator(strategy, format, lineup))
                .skip((long) batchIndex * STRATEGY_PAGE_SIZE)
                .limit(STRATEGY_PAGE_SIZE)
                .collect(Collectors.toList());

        List<TeamManagerDtos.LoanRecommendation> recommendations = new ArrayList<>();
        for (Player candidate : candidates) {
            if (lineup.size() < STANDARD_ROSTER_SIZE) {
                List<Player> candidateLineup = new ArrayList<>(lineup);
                candidateLineup.add(candidate);
                recommendations.add(buildLoanRecommendation(
                        request, currentTeam, teamsById, rewardRules, candidate, null, candidateLineup,
                        opponentLineup, rosterSize, leagueStandard, format, strategy));
            } else {
                Player replaced = selectReplacement(strategy, candidate, lineup);
                List<Player> candidateLineup = lineup.stream()
                        .filter(player -> !Objects.equals(player.getId(), replaced.getId()))
                        .collect(Collectors.toCollection(ArrayList::new));
                candidateLineup.add(candidate);
                recommendations.add(buildLoanRecommendation(
                        request, currentTeam, teamsById, rewardRules, candidate, replaced, candidateLineup,
                        opponentLineup, rosterSize, leagueStandard, format, strategy));
            }
        }
        recommendations.sort(recommendationComparator(strategy));

        TeamManagerDtos.StrategyRecommendation group = new TeamManagerDtos.StrategyRecommendation();
        group.setStrategy(strategy);
        group.setBatchIndex(batchIndex);
        group.setCandidateRange(candidateRange(strategy));
        group.setRecommendations(recommendations);
        return group;
    }

    private TeamManagerDtos.LoanRecommendation buildLoanRecommendation(
            TeamManagerDtos.SimulationRequest request,
            Team currentTeam,
            Map<Long, Team> teamsById,
            List<SettlementRewardRule> rewardRules,
            Player candidate,
            Player replaced,
            List<Player> candidateLineup,
            List<Player> opponentLineup,
            int rosterSize,
            double leagueStandard,
            String format,
            String strategy) {
        TeamManagerDtos.SimulationResponse simulation = buildSimulation(
                request, currentTeam, candidateLineup, opponentLineup, rewardRules, rosterSize, leagueStandard);
        TeamManagerDtos.LoanRecommendation recommendation = new TeamManagerDtos.LoanRecommendation();
        Team sourceTeam = teamsById.get(candidate.getTeamId());
        recommendation.setPlayerId(candidate.getId());
        recommendation.setPlayerName(candidate.getName());
        recommendation.setSourceTeamId(candidate.getTeamId());
        recommendation.setSourceTeamState(sourceTeam != null ? sourceTeam.getState() : "自由人");
        recommendation.setSourceType(candidate.getTeamId() == null ? "free_agent" : "original_team");
        recommendation.setReplacedPlayerId(replaced == null ? null : replaced.getId());
        recommendation.setReplacedPlayerName(replaced == null ? null : replaced.getName());
        recommendation.setPlayerValue(valueOrZero(candidate.getValue()));
        recommendation.setLoanFee(calculator.calculateLoanFee(
                valueOrZero(candidate.getValue()), format, recommendation.getSourceType()).fee());
        recommendation.setLineupPlayerIdsAfterLoan(candidateLineup.stream()
                .map(Player::getId)
                .collect(Collectors.toList()));
        recommendation.setScenarios(simulation.getScenarios());
        recommendation.setWorstNetPChange(simulation.getScenarios().stream()
                .map(TeamManagerDtos.ScenarioSimulation::getNetPChange)
                .min(Integer::compareTo)
                .orElse(0));
        recommendation.setBestNetPChange(simulation.getScenarios().stream()
                .map(TeamManagerDtos.ScenarioSimulation::getNetPChange)
                .max(Integer::compareTo)
                .orElse(0));
        return recommendation;
    }

    private Comparator<TeamManagerDtos.LoanRecommendation> recommendationComparator(String strategy) {
        if ("冲一冲".equals(strategy)) {
            return Comparator.comparing(TeamManagerDtos.LoanRecommendation::getPlayerValue).reversed()
                    .thenComparing(Comparator.comparing(TeamManagerDtos.LoanRecommendation::getBestNetPChange).reversed());
        }
        if ("保一保".equals(strategy)) {
            return Comparator.comparing(TeamManagerDtos.LoanRecommendation::getLoanFee)
                    .thenComparing(TeamManagerDtos.LoanRecommendation::getPlayerValue);
        }
        return Comparator.comparing((TeamManagerDtos.LoanRecommendation row) -> Math.abs(row.getWorstNetPChange()))
                .thenComparing(TeamManagerDtos.LoanRecommendation::getLoanFee);
    }

    private Comparator<Player> candidateComparator(String strategy, String format, List<Player> lineup) {
        if ("冲一冲".equals(strategy)) {
            return Comparator.comparing((Player player) -> valueOrZero(player.getValue())).reversed()
                    .thenComparing(Player::getId);
        }
        if ("保一保".equals(strategy)) {
            return Comparator.comparing((Player player) -> calculator.calculateLoanFee(
                    valueOrZero(player.getValue()),
                    format,
                    player.getTeamId() == null ? "free_agent" : "original_team").fee())
                    .thenComparing(Player::getId);
        }
        int targetValue = lineup.isEmpty()
                ? 1000
                : Math.toIntExact(Math.round(lineup.stream()
                .mapToInt(player -> valueOrZero(player.getValue()))
                .average()
                .orElse(1000)));
        return Comparator.comparing((Player player) -> Math.abs(valueOrZero(player.getValue()) - targetValue))
                .thenComparing((Player player) -> calculator.calculateLoanFee(
                        valueOrZero(player.getValue()),
                        format,
                        player.getTeamId() == null ? "free_agent" : "original_team").fee())
                .thenComparing(Player::getId);
    }

    private Player selectReplacement(String strategy, Player candidate, List<Player> lineup) {
        if ("冲一冲".equals(strategy)) {
            return lineup.stream()
                    .min(Comparator.comparing((Player player) -> valueOrZero(player.getValue()))
                            .thenComparing(Player::getId))
                    .orElseThrow();
        }
        if ("保一保".equals(strategy)) {
            return lineup.stream()
                    .max(Comparator.comparing((Player player) -> valueOrZero(player.getValue()))
                            .thenComparing(Player::getId))
                    .orElseThrow();
        }
        return lineup.stream()
                .min(Comparator.comparing((Player player) -> Math.abs(valueOrZero(player.getValue()) - valueOrZero(candidate.getValue())))
                        .thenComparing(Player::getId))
                .orElseThrow();
    }

    private int batchIndex(TeamManagerDtos.SimulationRequest request, String strategy) {
        if (request.getRecommendationBatches() == null) {
            return 0;
        }
        return Math.max(0, request.getRecommendationBatches().getOrDefault(strategy, 0));
    }

    private String candidateRange(String strategy) {
        if ("冲一冲".equals(strategy)) {
            return "高身价冲击上限：优先预筛身价更高、可提升阵容上限的候选。";
        }
        if ("保一保".equals(strategy)) {
            return "低成本控制风险：优先预筛租借费更低、税费压力更小的候选。";
        }
        return "中位稳健补强：优先预筛身价接近当前阵容均值、收益波动较小的候选。";
    }

    private int calculateReward(String format, String score, List<SettlementRewardRule> rewardRules) {
        String[] parts = score.split(":");
        int mine = Integer.parseInt(parts[0]);
        int opponent = Integer.parseInt(parts[1]);
        String canonicalScore = mine < opponent ? opponent + ":" + mine : score;
        SettlementRewardRule rule = rewardRules.stream()
                .filter(row -> row.getIsActive() == null || row.getIsActive() == 1)
                .filter(row -> format.equalsIgnoreCase(row.getFormat()))
                .filter(row -> score.equals(row.getScorePattern()) || canonicalScore.equals(row.getScorePattern()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "未配置 " + format + " " + canonicalScore + " 赛果奖励规则"));
        if (mine == opponent) {
            return valueOrZero(rule.getDrawAmount());
        }
        return mine > opponent ? valueOrZero(rule.getWinnerAmount()) : valueOrZero(rule.getLoserAmount());
    }

    private int calculateLuxuryTax(List<Player> players, int lineValue, int rosterSize, String format) {
        double leagueStandard = calculateLeagueStandard(players);
        return calculator.calculateLuxuryTax(leagueStandard, lineValue, rosterSize, format).tax();
    }

    private int calculateLoanFeePaid(Team currentTeam, List<Player> lineup, String format) {
        return lineup.stream()
                .filter(player -> !Objects.equals(player.getTeamId(), currentTeam.getId()))
                .mapToInt(player -> calculator.calculateLoanFee(
                        valueOrZero(player.getValue()),
                        format,
                        player.getTeamId() == null ? "free_agent" : "original_team").fee())
                .sum();
    }

    private double calculateLeagueStandard(List<Player> players) {
        List<Player> activePlayers = players.stream()
                .filter(player -> player.getStatus() == null || player.getStatus() == 1)
                .filter(player -> player.getValue() != null)
                .collect(Collectors.toList());
        if (activePlayers.isEmpty()) {
            throw new BusinessException(400, "缺少在职选手，无法计算奢侈税税线");
        }
        return activePlayers.stream().mapToInt(Player::getValue).average().orElse(0) * STANDARD_ROSTER_SIZE;
    }

    private int countRosterSize(Team currentTeam, List<Player> players) {
        long count = players.stream()
                .filter(player -> Objects.equals(player.getTeamId(), currentTeam.getId()))
                .filter(player -> player.getStatus() == null || player.getStatus() == 1)
                .count();
        return Math.toIntExact(Math.max(count, STANDARD_ROSTER_SIZE));
    }

    private List<String> scorePatterns(String format) {
        if ("BO3".equals(format)) {
            return List.of("2:0", "2:1", "1:2", "0:2");
        }
        return List.of("2:0", "1:1", "0:2");
    }

    private void requireRequest(TeamManagerDtos.SimulationRequest request, Team currentTeam) {
        if (currentTeam == null || currentTeam.getId() == null) {
            throw new BusinessException(401, "未找到当前用户队伍");
        }
        if (request == null) {
            throw new BusinessException(400, "缺少模拟请求");
        }
        if (request.getOpponentTeamId() == null) {
            throw new BusinessException(400, "必须选择对手");
        }
        if (request.getLineupPlayerIds() == null || request.getLineupPlayerIds().isEmpty()) {
            throw new BusinessException(400, "必须选择本场出场选手");
        }
        if (request.getOpponentLineupPlayerIds() == null || request.getOpponentLineupPlayerIds().isEmpty()) {
            throw new BusinessException(400, "必须选择敌方本场出场选手");
        }
        String format = normalizeFormat(request.getFormat());
        if (!new HashSet<>(List.of("BO2", "BO3")).contains(format)) {
            throw new BusinessException(400, "赛制仅支持 BO2 或 BO3");
        }
    }

    private List<Player> resolveOpponentLineup(TeamManagerDtos.SimulationRequest request, List<Player> players) {
        Set<Long> selectedIds = new HashSet<>(request.getOpponentLineupPlayerIds());
        return players.stream()
                .filter(player -> selectedIds.contains(player.getId()))
                .collect(Collectors.toList());
    }

    private String normalizeFormat(String format) {
        return format == null || format.isBlank() ? "BO2" : format.trim().toUpperCase();
    }

    private String normalizeStrategy(String strategy) {
        if ("冲一冲".equals(strategy) || "保一保".equals(strategy)) {
            return strategy;
        }
        return "稳一稳";
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
