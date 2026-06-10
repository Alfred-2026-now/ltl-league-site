package com.ltl.league.ai.service;

import com.ltl.league.ai.dto.TeamManagerDtos;
import com.ltl.league.admin.dto.RuleParameterVO;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.SettlementRewardRule;
import com.ltl.league.entity.Team;
import com.ltl.league.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TeamManagerReadOnlyToolService {

    private static final Set<String> SUPPORTED_TOOLS = Set.of(
            "get_current_team",
            "get_team_detail",
            "list_teams",
            "search_players",
            "get_rule_parameters",
            "get_reward_rules",
            "get_current_match_input");

    public String toolCatalog() {
        return "可用只读工具："
                + "get_current_team()；"
                + "get_team_detail(teamId)；"
                + "list_teams()；"
                + "search_players(keyword, teamId, status)；"
                + "get_rule_parameters(groupKey)；"
                + "get_reward_rules(format)；"
                + "get_current_match_input()。"
                + "如需工具，请只返回 JSON：{\"toolCalls\":[{\"name\":\"工具名\",\"arguments\":{}}]}。"
                + "每轮最多请求 3 个工具，禁止请求写入、修改、删除或任意 URL。";
    }

    public Map<String, Object> execute(
            TeamManagerDtos.ContextResponse context,
            TeamManagerDtos.ChatRequest request,
            String name,
            Map<String, Object> arguments) {
        if (!SUPPORTED_TOOLS.contains(name)) {
            throw new BusinessException(400, "不支持的只读工具: " + name);
        }
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        switch (name) {
            case "get_current_team":
                return currentTeam(context);
            case "get_team_detail":
                return teamDetail(context, asLong(args.get("teamId")));
            case "list_teams":
                return Map.of("teams", safeTeams(context));
            case "search_players":
                return searchPlayers(context, args);
            case "get_rule_parameters":
                return ruleParameters(context, asString(args.get("groupKey")));
            case "get_reward_rules":
                return rewardRules(context, asString(args.get("format")));
            case "get_current_match_input":
                return currentMatchInput(request);
            default:
                throw new BusinessException(400, "不支持的只读工具: " + name);
        }
    }

    private Map<String, Object> currentTeam(TeamManagerDtos.ContextResponse context) {
        Team currentTeam = context == null ? null : context.getCurrentTeam();
        if (currentTeam == null || currentTeam.getId() == null) {
            return Map.of("team", null);
        }
        return teamDetail(context, currentTeam.getId());
    }

    private Map<String, Object> teamDetail(TeamManagerDtos.ContextResponse context, Long teamId) {
        if (teamId == null) {
            throw new BusinessException(400, "get_team_detail 需要 teamId");
        }
        Team team = teamsById(context).get(teamId);
        if (team == null) {
            throw new BusinessException(404, "队伍不存在: " + teamId);
        }
        Map<String, Object> result = new LinkedHashMap<>(safeTeam(team));
        result.put("players", safePlayers(context).stream()
                .filter(player -> Objects.equals(player.get("teamId"), teamId))
                .collect(Collectors.toList()));
        return result;
    }

    private Map<String, Object> searchPlayers(TeamManagerDtos.ContextResponse context, Map<String, Object> args) {
        String keyword = asString(args.get("keyword"));
        Long teamId = asLong(args.get("teamId"));
        Integer status = asInteger(args.get("status"));
        List<Map<String, Object>> players = safePlayers(context).stream()
                .filter(player -> isBlank(keyword) || String.valueOf(player.get("name")).contains(keyword))
                .filter(player -> teamId == null || Objects.equals(player.get("teamId"), teamId))
                .filter(player -> status == null || Objects.equals(player.get("status"), status))
                .limit(30)
                .collect(Collectors.toList());
        return Map.of("players", players);
    }

    private Map<String, Object> ruleParameters(TeamManagerDtos.ContextResponse context, String groupKey) {
        List<Map<String, Object>> rows = context == null || context.getRuleParameters() == null
                ? List.of()
                : context.getRuleParameters().stream()
                .filter(parameter -> parameter.getIsActive() == null || parameter.getIsActive() == 1)
                .filter(parameter -> isBlank(groupKey) || groupKey.equals(parameter.getGroupKey()))
                .map(this::safeRuleParameter)
                .collect(Collectors.toList());
        return Map.of("parameters", rows);
    }

    private Map<String, Object> rewardRules(TeamManagerDtos.ContextResponse context, String format) {
        List<Map<String, Object>> rows = context == null || context.getRewardRules() == null
                ? List.of()
                : context.getRewardRules().stream()
                .filter(rule -> rule.getIsActive() == null || rule.getIsActive() == 1)
                .filter(rule -> rule.getDeleted() == null || rule.getDeleted() == 0)
                .filter(rule -> isBlank(format) || format.equalsIgnoreCase(rule.getFormat()))
                .map(this::safeRewardRule)
                .collect(Collectors.toList());
        return Map.of("rewardRules", rows);
    }

    private Map<String, Object> currentMatchInput(TeamManagerDtos.ChatRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("input", request == null ? null : request.getInput());
        result.put("simulation", request == null ? null : request.getSimulation());
        return result;
    }

    private List<Map<String, Object>> safeTeams(TeamManagerDtos.ContextResponse context) {
        return context == null || context.getTeams() == null
                ? List.of()
                : context.getTeams().stream().map(this::safeTeam).collect(Collectors.toList());
    }

    private Map<String, Object> safeTeam(Team team) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", team.getId());
        row.put("state", team.getState());
        row.put("name", team.getName());
        row.put("pCoins", team.getPCoins());
        row.put("points", team.getPoints());
        row.put("rank", team.getRank());
        return row;
    }

    private List<Map<String, Object>> safePlayers(TeamManagerDtos.ContextResponse context) {
        Map<Long, Team> teamsById = teamsById(context);
        return context == null || context.getPlayers() == null
                ? List.of()
                : context.getPlayers().stream().map(player -> safePlayer(player, teamsById)).collect(Collectors.toList());
    }

    private Map<String, Object> safePlayer(Player player, Map<Long, Team> teamsById) {
        Team team = player.getTeamId() == null ? null : teamsById.get(player.getTeamId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", player.getId());
        row.put("teamId", player.getTeamId());
        row.put("teamState", team == null ? null : team.getState());
        row.put("name", player.getName());
        row.put("value", player.getValue());
        row.put("status", player.getStatus());
        row.put("isLoan", player.getIsLoan());
        row.put("loanTeamId", player.getLoanTeamId());
        row.put("deposit", player.getDeposit());
        return row;
    }

    private Map<String, Object> safeRuleParameter(RuleParameterVO parameter) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("paramKey", parameter.getParamKey());
        row.put("groupKey", parameter.getGroupKey());
        row.put("groupName", parameter.getGroupName());
        row.put("name", parameter.getName());
        row.put("description", parameter.getDescription());
        row.put("valueType", parameter.getValueType());
        row.put("valueText", parameter.getValueText());
        row.put("unit", parameter.getUnit());
        return row;
    }

    private Map<String, Object> safeRewardRule(SettlementRewardRule rule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("format", rule.getFormat());
        row.put("scorePattern", rule.getScorePattern());
        row.put("winnerAmount", rule.getWinnerAmount());
        row.put("loserAmount", rule.getLoserAmount());
        row.put("drawAmount", rule.getDrawAmount());
        return row;
    }

    private Map<Long, Team> teamsById(TeamManagerDtos.ContextResponse context) {
        return context == null || context.getTeams() == null
                ? Map.of()
                : context.getTeams().stream()
                .filter(team -> team.getId() != null)
                .collect(Collectors.toMap(Team::getId, Function.identity(), (left, right) -> left));
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.valueOf(text);
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Integer.valueOf(text);
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
