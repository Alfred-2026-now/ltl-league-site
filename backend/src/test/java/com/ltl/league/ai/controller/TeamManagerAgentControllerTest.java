package com.ltl.league.ai.controller;

import com.ltl.league.admin.service.RuleParameterService;
import com.ltl.league.dto.UserInfoVO;
import com.ltl.league.entity.Team;
import com.ltl.league.mapper.SettlementRewardRuleMapper;
import com.ltl.league.service.PlayerService;
import com.ltl.league.service.TeamService;
import com.ltl.league.service.UserService;
import com.ltl.league.util.AuthUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamManagerAgentControllerTest {

    @Test
    void contextAllowsLoggedInNonAdminUsersWithTeam() {
        AuthUtil authUtil = mock(AuthUtil.class);
        UserService userService = mock(UserService.class);
        TeamService teamService = mock(TeamService.class);
        PlayerService playerService = mock(PlayerService.class);
        RuleParameterService ruleParameterService = mock(RuleParameterService.class);
        SettlementRewardRuleMapper rewardRuleMapper = mock(SettlementRewardRuleMapper.class);
        TeamManagerAgentController controller = new TeamManagerAgentController(
                authUtil,
                userService,
                teamService,
                playerService,
                ruleParameterService,
                rewardRuleMapper,
                null,
                null);

        when(authUtil.parseCookieValue("token")).thenReturn(new AuthUtil.CookieData(1L, "ZerstaN", 0, System.currentTimeMillis()));
        UserInfoVO user = new UserInfoVO();
        user.setPlayerId(1L);
        user.setRole(0);
        user.setTeamId(1L);
        when(userService.getUserInfo(1L)).thenReturn(user);
        Team team = new Team();
        team.setId(1L);
        team.setState("秦");
        team.setName("秦队");
        when(teamService.getAllTeams()).thenReturn(List.of(team));
        when(playerService.getAllPlayers()).thenReturn(List.of());
        when(ruleParameterService.listParameters(null)).thenReturn(List.of());
        when(rewardRuleMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        var result = controller.context("token");

        assertEquals(200, result.getCode());
        assertEquals(1L, result.getData().getCurrentTeam().getId());
    }
}
