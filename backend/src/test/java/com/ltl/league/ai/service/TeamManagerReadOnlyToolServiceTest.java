package com.ltl.league.ai.service;

import com.ltl.league.ai.dto.TeamManagerDtos;
import com.ltl.league.admin.dto.RuleParameterVO;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.SettlementRewardRule;
import com.ltl.league.entity.Team;
import com.ltl.league.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamManagerReadOnlyToolServiceTest {

    private final TeamManagerReadOnlyToolService service = new TeamManagerReadOnlyToolService();

    @Test
    void executeGetTeamDetailReturnsSanitizedTeamAndPlayers() {
        Map<String, Object> result = service.execute(
                context(),
                new TeamManagerDtos.ChatRequest(),
                "get_team_detail",
                Map.of("teamId", 1));

        assertEquals("秦", result.get("state"));
        assertEquals(2, ((List<?>) result.get("players")).size());
        assertTrue(result.toString().contains("秦一"));
    }

    @Test
    void executeGetRuleParametersCanFilterByGroupKey() {
        Map<String, Object> result = service.execute(
                context(),
                new TeamManagerDtos.ChatRequest(),
                "get_rule_parameters",
                Map.of("groupKey", "loan"));

        assertEquals(1, ((List<?>) result.get("parameters")).size());
        assertTrue(result.toString().contains("loan.bo3.rate"));
    }

    @Test
    void executeRejectsUnknownToolNames() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.execute(
                context(),
                new TeamManagerDtos.ChatRequest(),
                "delete_team",
                Map.of("teamId", 1)));

        assertEquals(400, error.getCode());
        assertEquals("不支持的只读工具: delete_team", error.getMessage());
    }

    private TeamManagerDtos.ContextResponse context() {
        TeamManagerDtos.ContextResponse context = new TeamManagerDtos.ContextResponse();
        context.setCurrentTeam(team(1L, "秦", "秦队", 5000));
        context.setTeams(List.of(
                team(1L, "秦", "秦队", 5000),
                team(2L, "楚", "楚队", 4200)));
        context.setPlayers(List.of(
                player(1L, 1L, "秦一", 1600),
                player(2L, 1L, "秦二", 1200),
                player(3L, 2L, "楚一", 1500)));
        context.setRuleParameters(List.of(
                ruleParameter("loan.bo3.rate", "loan", "租借", "BO3 租借费比例", "0.6", "倍"),
                ruleParameter("luxury.tax_line_factor", "luxury", "奢侈税", "税线系数", "1.3", "倍")));
        context.setRewardRules(List.of(rewardRule("BO3", "2:1", 1800, 800, null)));
        return context;
    }

    private Team team(Long id, String state, String name, Integer pCoins) {
        Team team = new Team();
        team.setId(id);
        team.setState(state);
        team.setName(name);
        team.setPCoins(pCoins);
        team.setPoints(10);
        team.setRank(1);
        return team;
    }

    private Player player(Long id, Long teamId, String name, Integer value) {
        Player player = new Player();
        player.setId(id);
        player.setTeamId(teamId);
        player.setName(name);
        player.setValue(value);
        player.setStatus(1);
        return player;
    }

    private RuleParameterVO ruleParameter(String key, String groupKey, String groupName, String name, String value, String unit) {
        RuleParameterVO parameter = new RuleParameterVO();
        parameter.setParamKey(key);
        parameter.setGroupKey(groupKey);
        parameter.setGroupName(groupName);
        parameter.setName(name);
        parameter.setValueText(value);
        parameter.setUnit(unit);
        parameter.setIsActive(1);
        return parameter;
    }

    private SettlementRewardRule rewardRule(String format, String score, Integer winner, Integer loser, Integer draw) {
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
