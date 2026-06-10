package com.ltl.league.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltl.league.ai.dto.TeamManagerDtos;
import com.ltl.league.admin.dto.RuleParameterVO;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.SettlementRewardRule;
import com.ltl.league.entity.Team;
import com.ltl.league.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DeepSeekTeamManagerChatService {

    private static final int MAX_TOOL_CALLS = 3;

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final RestTemplate restTemplate;
    private final TeamManagerReadOnlyToolService toolService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public DeepSeekTeamManagerChatService(
            @Value("${ltl.ai.deepseek.api-key:}") String apiKey,
            @Value("${ltl.ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${ltl.ai.deepseek.model:deepseek-v4-flash}") String model,
            @Value("${ltl.ai.deepseek.timeout-ms:60000}") int timeoutMs,
            TeamManagerReadOnlyToolService toolService) {
        this(apiKey, baseUrl, model, timeoutMs, new RestTemplateBuilder(), toolService);
    }

    DeepSeekTeamManagerChatService(
            String apiKey,
            String baseUrl,
            String model,
            int timeoutMs) {
        this(apiKey, baseUrl, model, timeoutMs, new RestTemplateBuilder(), new TeamManagerReadOnlyToolService());
    }

    private DeepSeekTeamManagerChatService(
            String apiKey,
            String baseUrl,
            String model,
            int timeoutMs,
            RestTemplateBuilder builder,
            TeamManagerReadOnlyToolService toolService) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = stripTrailingSlash(baseUrl == null ? "https://api.deepseek.com" : baseUrl);
        this.model = model == null || model.isBlank() ? "deepseek-v4-flash" : model.trim();
        this.toolService = toolService;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public TeamManagerDtos.ChatResponse chat(
            TeamManagerDtos.ContextResponse context,
            TeamManagerDtos.ChatRequest request) {
        if (apiKey.isBlank()) {
            throw new BusinessException(500, "DeepSeek API Key 未配置，请在服务端设置 DEEPSEEK_API_KEY");
        }

        try {
            List<Map<String, Object>> toolResults = resolveToolResults(context, request);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/chat/completions",
                    new HttpEntity<>(buildPayload(context, request, false, toolResults), buildHeaders()),
                    Map.class);
            String reply = extractReply(response.getBody());
            TeamManagerDtos.ChatResponse chatResponse = new TeamManagerDtos.ChatResponse();
            chatResponse.setReply(reply);
            return chatResponse;
        } catch (RestClientException e) {
            throw new BusinessException(502, "DeepSeek 调用失败：" + e.getMessage());
        }
    }

    public void streamChat(
            TeamManagerDtos.ContextResponse context,
            TeamManagerDtos.ChatRequest request,
            Consumer<String> onDelta) {
        if (apiKey.isBlank()) {
            throw new BusinessException(500, "DeepSeek API Key 未配置，请在服务端设置 DEEPSEEK_API_KEY");
        }
        try {
            List<Map<String, Object>> toolResults = resolveToolResults(context, request);
            restTemplate.execute(
                    baseUrl + "/chat/completions",
                    HttpMethod.POST,
                    clientRequest -> {
                        clientRequest.getHeaders().putAll(buildHeaders());
                        objectMapper.writeValue(clientRequest.getBody(), buildPayload(context, request, true, toolResults));
                    },
                    response -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new BusinessException(502, "DeepSeek 调用失败：" + response.getStatusCode());
                        }
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                String data = line.substring(5).trim();
                                if (data.isBlank() || "[DONE]".equals(data)) {
                                    continue;
                                }
                                String delta = extractStreamDelta(objectMapper.readValue(data, Map.class));
                                if (!delta.isBlank()) {
                                    onDelta.accept(delta);
                                }
                            }
                        }
                        return null;
                    });
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(502, "DeepSeek 流式调用失败：" + e.getMessage());
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private Map<String, Object> buildPayload(
            TeamManagerDtos.ContextResponse context,
            TeamManagerDtos.ChatRequest request,
            boolean stream,
            List<Map<String, Object>> toolResults) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", buildMessages(context, request, toolResults));
        payload.put("temperature", 0.3);
        payload.put("max_tokens", 2400);
        payload.put("stream", stream);
        return payload;
    }

    private List<Map<String, String>> buildMessages(
            TeamManagerDtos.ContextResponse context,
            TeamManagerDtos.ChatRequest request,
            List<Map<String, Object>> toolResults) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system",
                "你是 LTL 联赛的 P哥推荐 Agent。你只能基于提供的规则、队伍、选手、用户队伍和模拟结果进行建议。"
                        + "数字账项必须引用后端模拟结果，不要自行编造。不要声称已经写入赛果、P币流水或正式比赛数据。"
                        + "你可以使用服务端只读工具查询队伍、选手和规则，但不能修改任何数据。"
                        + "回答使用简洁 Markdown，小标题、列表和重点即可；如果推荐选手，请基于模拟推荐、收益区间、租借费、替换对象和规则参数自行给出理由。"));
        messages.add(message("user", "当前可信上下文：\n" + summarizeContext(context, request)));
        if (toolResults != null && !toolResults.isEmpty()) {
            messages.add(message("user", "服务端只读工具查询结果：\n" + toJson(toolResults)));
        }
        if (request != null && request.getMessages() != null) {
            for (TeamManagerDtos.ChatMessage item : request.getMessages()) {
                if (item == null || item.getContent() == null || item.getContent().isBlank()) {
                    continue;
                }
                String role = "assistant".equals(item.getRole()) ? "assistant" : "user";
                messages.add(message(role, item.getContent()));
            }
        }
        return messages;
    }

    private List<Map<String, Object>> resolveToolResults(
            TeamManagerDtos.ContextResponse context,
            TeamManagerDtos.ChatRequest request) {
        String plannerReply = requestToolPlan(context, request);
        List<ToolCall> calls = parseToolCalls(plannerReply);
        if (calls.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (ToolCall call : calls.stream().limit(MAX_TOOL_CALLS).collect(Collectors.toList())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", call.name());
            row.put("arguments", call.arguments());
            row.put("result", toolService.execute(context, request, call.name(), call.arguments()));
            results.add(row);
        }
        return results;
    }

    private String requestToolPlan(
            TeamManagerDtos.ContextResponse context,
            TeamManagerDtos.ChatRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", 0);
        payload.put("max_tokens", 400);
        payload.put("stream", false);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", "你是工具规划器。只能判断是否需要只读工具。"
                + toolService.toolCatalog()
                + "如果不需要工具，返回 {\"toolCalls\":[]}。不要返回自然语言。"));
        messages.add(message("user", "可信上下文摘要：\n" + summarizeContext(context, request)
                + "\n用户最新问题和历史：\n" + summarizeConversation(request)));
        payload.put("messages", messages);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/chat/completions",
                    new HttpEntity<>(payload, buildHeaders()),
                    Map.class);
            return extractReply(response.getBody());
        } catch (RestClientException e) {
            return "{\"toolCalls\":[]}";
        }
    }

    private String summarizeConversation(TeamManagerDtos.ChatRequest request) {
        if (request == null || request.getMessages() == null) {
            return "";
        }
        return request.getMessages().stream()
                .filter(item -> item != null && item.getContent() != null && !item.getContent().isBlank())
                .map(item -> ("assistant".equals(item.getRole()) ? "assistant" : "user") + ": " + item.getContent())
                .collect(Collectors.joining("\n"));
    }

    private List<ToolCall> parseToolCalls(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String json = value.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            Map<?, ?> root = objectMapper.readValue(json, Map.class);
            Object rawCalls = root.get("toolCalls");
            if (!(rawCalls instanceof List)) {
                return List.of();
            }
            List<ToolCall> calls = new ArrayList<>();
            for (Object rawCall : (List<?>) rawCalls) {
                if (!(rawCall instanceof Map)) {
                    continue;
                }
                Map<?, ?> call = (Map<?, ?>) rawCall;
                Object name = call.get("name");
                Object args = call.get("arguments");
                if (name == null) {
                    continue;
                }
                calls.add(new ToolCall(String.valueOf(name), args instanceof Map ? castMap((Map<?, ?>) args) : Map.of()));
            }
            return calls;
        } catch (IOException e) {
            return List.of();
        }
    }

    private Map<String, Object> castMap(Map<?, ?> input) {
        Map<String, Object> output = new LinkedHashMap<>();
        input.forEach((key, value) -> output.put(String.valueOf(key), value));
        return output;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            return String.valueOf(value);
        }
    }

    private String summarizeContext(TeamManagerDtos.ContextResponse context, TeamManagerDtos.ChatRequest request) {
        StringBuilder builder = new StringBuilder();
        if (context != null) {
            if (context.getCurrentUser() != null) {
                builder.append("当前用户队伍：")
                        .append(context.getCurrentUser().getTeamState())
                        .append(" / ")
                        .append(context.getCurrentUser().getTeamName())
                        .append('\n');
            }
            builder.append("规则摘要：").append(context.getKnowledgeSummary()).append('\n');
            builder.append(summarizeRuleParameters(context.getRuleParameters()));
            builder.append(summarizeRewardRules(context.getRewardRules()));
            builder.append("队伍数：").append(context.getTeams() == null ? 0 : context.getTeams().size()).append('\n');
            builder.append("选手数：").append(context.getPlayers() == null ? 0 : context.getPlayers().size()).append('\n');
        }
        if (request != null && request.getInput() != null) {
            Map<Long, Player> playersById = playersById(context);
            Map<Long, Team> teamsById = teamsById(context);
            builder.append("本场输入：赛制=").append(request.getInput().getFormat())
                    .append("，对手ID=").append(request.getInput().getOpponentTeamId())
                    .append('\n')
                    .append("我方出场：")
                    .append(describeLineup(request.getInput().getLineupPlayerIds(), playersById, teamsById, context == null ? null : context.getCurrentTeam()))
                    .append('\n')
                    .append("敌方出场：")
                    .append(describeLineup(request.getInput().getOpponentLineupPlayerIds(), playersById, teamsById, teamById(teamsById, request.getInput().getOpponentTeamId())))
                    .append('\n');
        }
        if (request != null && request.getSimulation() != null) {
            builder.append("模拟结果：");
            for (TeamManagerDtos.ScenarioSimulation row : request.getSimulation().getScenarios()) {
                builder.append(row.getScorePattern())
                        .append(" 净变化 ")
                        .append(row.getNetPChange())
                        .append("P，余额 ")
                        .append(row.getBalanceAfter())
                        .append("P；");
            }
            builder.append('\n');
            if (request.getSimulation().getStrategyRecommendations() != null
                    && !request.getSimulation().getStrategyRecommendations().isEmpty()) {
                builder.append("策略推荐：\n");
                for (TeamManagerDtos.StrategyRecommendation group : request.getSimulation().getStrategyRecommendations()) {
                    builder.append(group.getStrategy()).append("：");
                    builder.append(group.getRecommendations().stream()
                            .map(row -> row.getPlayerName()
                                    + "(" + valueOrZero(row.getPlayerValue()) + "P"
                                    + (row.getReplacedPlayerName() == null ? "" : "，替换" + row.getReplacedPlayerName())
                                    + "，" + row.getWorstNetPChange() + "~" + row.getBestNetPChange() + "P"
                                    + ")")
                            .collect(Collectors.joining("；")));
                    builder.append('\n');
                }
            }
        }
        return builder.toString();
    }

    private String summarizeRuleParameters(List<RuleParameterVO> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "规则参数明细：未配置\n";
        }
        Map<String, List<RuleParameterVO>> grouped = parameters.stream()
                .filter(parameter -> parameter.getIsActive() == null || parameter.getIsActive() == 1)
                .sorted((left, right) -> Integer.compare(valueOrMax(left.getSortOrder()), valueOrMax(right.getSortOrder())))
                .collect(Collectors.groupingBy(
                        parameter -> isBlank(parameter.getGroupName()) ? "未分组" : parameter.getGroupName(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        StringBuilder builder = new StringBuilder("规则参数明细：\n");
        grouped.forEach((group, rows) -> {
            builder.append("[").append(group).append("] ");
            builder.append(rows.stream()
                    .map(this::describeRuleParameter)
                    .collect(Collectors.joining("；")));
            builder.append('\n');
        });
        return builder.toString();
    }

    private String describeRuleParameter(RuleParameterVO parameter) {
        StringBuilder builder = new StringBuilder();
        if (!isBlank(parameter.getParamKey())) {
            builder.append(parameter.getParamKey()).append(" ");
        }
        builder.append(isBlank(parameter.getName()) ? "参数" : parameter.getName())
                .append("=")
                .append(parameter.getValueText() == null ? "" : parameter.getValueText());
        if (!isBlank(parameter.getUnit())) {
            builder.append(parameter.getUnit());
        }
        if (!isBlank(parameter.getDescription())) {
            builder.append("（").append(parameter.getDescription()).append("）");
        }
        return builder.toString();
    }

    private String summarizeRewardRules(List<SettlementRewardRule> rewardRules) {
        if (rewardRules == null || rewardRules.isEmpty()) {
            return "赛果奖励规则：未配置\n";
        }
        String rows = rewardRules.stream()
                .filter(rule -> rule.getIsActive() == null || rule.getIsActive() == 1)
                .filter(rule -> rule.getDeleted() == null || rule.getDeleted() == 0)
                .map(this::describeRewardRule)
                .collect(Collectors.joining("；"));
        return "赛果奖励规则：" + rows + "\n";
    }

    private String describeRewardRule(SettlementRewardRule rule) {
        StringBuilder builder = new StringBuilder();
        builder.append(rule.getFormat()).append(" ").append(rule.getScorePattern());
        if (rule.getDrawAmount() != null) {
            builder.append(" 平局 ").append(rule.getDrawAmount()).append("P");
        } else {
            builder.append(" 胜方 ").append(valueOrZero(rule.getWinnerAmount())).append("P")
                    .append(" 败方 ").append(valueOrZero(rule.getLoserAmount())).append("P");
        }
        return builder.toString();
    }

    private int valueOrMax(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Map<Long, Player> playersById(TeamManagerDtos.ContextResponse context) {
        if (context == null || context.getPlayers() == null) {
            return Map.of();
        }
        return context.getPlayers().stream()
                .filter(player -> player.getId() != null)
                .collect(Collectors.toMap(Player::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, Team> teamsById(TeamManagerDtos.ContextResponse context) {
        if (context == null || context.getTeams() == null) {
            return Map.of();
        }
        return context.getTeams().stream()
                .filter(team -> team.getId() != null)
                .collect(Collectors.toMap(Team::getId, Function.identity(), (left, right) -> left));
    }

    private Team teamById(Map<Long, Team> teamsById, Long teamId) {
        return teamId == null ? null : teamsById.get(teamId);
    }

    private String describeLineup(
            List<Long> playerIds,
            Map<Long, Player> playersById,
            Map<Long, Team> teamsById,
            Team expectedTeam) {
        if (playerIds == null || playerIds.isEmpty()) {
            return "未选择";
        }
        return playerIds.stream()
                .map(playersById::get)
                .filter(Objects::nonNull)
                .map(player -> describePlayer(player, teamsById, expectedTeam))
                .collect(Collectors.joining("；"));
    }

    private String describePlayer(Player player, Map<Long, Team> teamsById, Team expectedTeam) {
        Team sourceTeam = player.getTeamId() == null ? null : teamsById.get(player.getTeamId());
        boolean loan = expectedTeam != null && !Objects.equals(player.getTeamId(), expectedTeam.getId());
        return player.getName()
                + "(" + valueOrZero(player.getValue()) + "P"
                + "，所属=" + (sourceTeam == null ? "自由人" : sourceTeam.getState())
                + (loan ? "，租借" : "")
                + ")";
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String extractReply(Map body) {
        if (body == null) {
            throw new BusinessException(502, "DeepSeek 返回为空");
        }
        Object choicesValue = body.get("choices");
        if (!(choicesValue instanceof List) || ((List<?>) choicesValue).isEmpty()) {
            throw new BusinessException(502, "DeepSeek 返回缺少 choices");
        }
        Object first = ((List<?>) choicesValue).get(0);
        if (!(first instanceof Map)) {
            throw new BusinessException(502, "DeepSeek 返回格式异常");
        }
        Object messageValue = ((Map<?, ?>) first).get("message");
        if (!(messageValue instanceof Map)) {
            throw new BusinessException(502, "DeepSeek 返回缺少 message");
        }
        Object content = ((Map<?, ?>) messageValue).get("content");
        return content == null ? "" : String.valueOf(content);
    }

    private String extractStreamDelta(Map body) {
        if (body == null) {
            return "";
        }
        Object choicesValue = body.get("choices");
        if (!(choicesValue instanceof List) || ((List<?>) choicesValue).isEmpty()) {
            return "";
        }
        Object first = ((List<?>) choicesValue).get(0);
        if (!(first instanceof Map)) {
            return "";
        }
        Object deltaValue = ((Map<?, ?>) first).get("delta");
        if (!(deltaValue instanceof Map)) {
            return "";
        }
        Object content = ((Map<?, ?>) deltaValue).get("content");
        return content == null ? "" : String.valueOf(content);
    }

    private String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    private record ToolCall(String name, Map<String, Object> arguments) {
    }
}
