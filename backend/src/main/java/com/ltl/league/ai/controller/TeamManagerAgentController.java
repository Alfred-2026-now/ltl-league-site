package com.ltl.league.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltl.league.ai.dto.TeamManagerDtos;
import com.ltl.league.ai.service.DeepSeekTeamManagerChatService;
import com.ltl.league.ai.service.TeamManagerSimulationService;
import com.ltl.league.admin.service.RuleParameterService;
import com.ltl.league.common.Result;
import com.ltl.league.dto.UserInfoVO;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.SettlementRewardRule;
import com.ltl.league.entity.Team;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.SettlementRewardRuleMapper;
import com.ltl.league.service.PlayerService;
import com.ltl.league.service.TeamService;
import com.ltl.league.service.UserService;
import com.ltl.league.util.AuthUtil;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/ai/team-manager")
public class TeamManagerAgentController {

    private static final String COOKIE_NAME = "ltl_auth";

    private final AuthUtil authUtil;
    private final UserService userService;
    private final TeamService teamService;
    private final PlayerService playerService;
    private final RuleParameterService ruleParameterService;
    private final SettlementRewardRuleMapper rewardRuleMapper;
    private final TeamManagerSimulationService simulationService;
    private final DeepSeekTeamManagerChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TeamManagerAgentController(
            AuthUtil authUtil,
            UserService userService,
            TeamService teamService,
            PlayerService playerService,
            RuleParameterService ruleParameterService,
            SettlementRewardRuleMapper rewardRuleMapper,
            TeamManagerSimulationService simulationService,
            DeepSeekTeamManagerChatService chatService) {
        this.authUtil = authUtil;
        this.userService = userService;
        this.teamService = teamService;
        this.playerService = playerService;
        this.ruleParameterService = ruleParameterService;
        this.rewardRuleMapper = rewardRuleMapper;
        this.simulationService = simulationService;
        this.chatService = chatService;
    }

    @GetMapping("/context")
    public Result<TeamManagerDtos.ContextResponse> context(
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        return Result.success(loadContext(requireUser(token)));
    }

    @PostMapping("/simulate")
    public Result<TeamManagerDtos.SimulationResponse> simulate(
            @CookieValue(value = COOKIE_NAME, required = false) String token,
            @RequestBody TeamManagerDtos.SimulationRequest request) {
        UserInfoVO user = requireUser(token);
        TeamManagerDtos.ContextResponse context = loadContext(user);
        return Result.success(simulationService.simulate(
                request,
                context.getCurrentTeam(),
                context.getTeams(),
                context.getPlayers(),
                context.getRewardRules()));
    }

    @PostMapping("/chat")
    public Result<TeamManagerDtos.ChatResponse> chat(
            @CookieValue(value = COOKIE_NAME, required = false) String token,
            @RequestBody TeamManagerDtos.ChatRequest request) {
        UserInfoVO user = requireUser(token);
        TeamManagerDtos.ContextResponse context = loadContext(user);
        return Result.success(chatService.chat(context, request));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody chatStream(
            @CookieValue(value = COOKIE_NAME, required = false) String token,
            @RequestBody TeamManagerDtos.ChatRequest request) {
        UserInfoVO user = requireUser(token);
        TeamManagerDtos.ContextResponse context = loadContext(user);
        return output -> {
            try {
                chatService.streamChat(context, request, delta -> {
                    try {
                        sendEvent(output, "delta", delta);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                sendEvent(output, "done", "");
            } catch (Exception e) {
                sendEvent(output, "error", e.getMessage() == null ? "DeepSeek 流式调用失败" : e.getMessage());
            }
        };
    }

    private void sendEvent(OutputStream output, String event, String data) throws IOException {
        output.write(("event: " + event + "\n").getBytes(StandardCharsets.UTF_8));
        output.write(("data: " + objectMapper.writeValueAsString(data == null ? "" : data) + "\n\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private UserInfoVO requireUser(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        AuthUtil.CookieData cookieData = authUtil.parseCookieValue(token);
        if (cookieData == null) {
            throw new BusinessException(401, "登录已过期");
        }
        UserInfoVO user = userService.getUserInfo(cookieData.getPlayerId());
        if (user.getTeamId() == null) {
            throw new BusinessException(400, "当前用户尚未加入队伍");
        }
        return user;
    }

    private TeamManagerDtos.ContextResponse loadContext(UserInfoVO user) {
        List<Team> teams = teamService.getAllTeams();
        List<Player> players = playerService.getAllPlayers();
        Team currentTeam = teams.stream()
                .filter(team -> Objects.equals(team.getId(), user.getTeamId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "当前用户队伍不存在"));

        TeamManagerDtos.ContextResponse response = new TeamManagerDtos.ContextResponse();
        response.setCurrentUser(user);
        response.setCurrentTeam(currentTeam);
        response.setTeams(teams);
        response.setPlayers(players);
        response.setRuleParameters(ruleParameterService.listParameters(null));
        response.setRewardRules(activeRewardRules());
        response.setKnowledgeSummary("规则知识库包含比赛奖励、奢侈税、租借费、队伍余额、选手身价和当前登录用户队伍。"
                + "后端模拟结果为账本依据，AI 只负责解释和建议。");
        return response;
    }

    private List<SettlementRewardRule> activeRewardRules() {
        return rewardRuleMapper.selectList(new LambdaQueryWrapper<SettlementRewardRule>()
                .eq(SettlementRewardRule::getIsActive, 1)
                .eq(SettlementRewardRule::getDeleted, 0)
                .orderByAsc(SettlementRewardRule::getFormat)
                .orderByAsc(SettlementRewardRule::getScorePattern));
    }
}
