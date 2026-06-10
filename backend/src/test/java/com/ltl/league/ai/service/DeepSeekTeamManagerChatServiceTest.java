package com.ltl.league.ai.service;

import com.ltl.league.ai.dto.TeamManagerDtos;
import com.ltl.league.admin.dto.RuleParameterVO;
import com.ltl.league.entity.SettlementRewardRule;
import com.ltl.league.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeepSeekTeamManagerChatServiceTest {

    @Test
    void chatRejectsMissingApiKeyBeforeCallingDeepSeek() {
        DeepSeekTeamManagerChatService service = new DeepSeekTeamManagerChatService(
                "",
                "https://api.deepseek.com",
                "deepseek-v4-flash",
                8000);

        assertThrows(BusinessException.class,
                () -> service.chat(new TeamManagerDtos.ContextResponse(), new TeamManagerDtos.ChatRequest()));
    }

    @Test
    void trustedContextIncludesRuleParametersAndRewardRules() throws Exception {
        DeepSeekTeamManagerChatService service = new DeepSeekTeamManagerChatService(
                "key",
                "https://api.deepseek.com",
                "deepseek-v4-flash",
                8000);
        TeamManagerDtos.ContextResponse context = new TeamManagerDtos.ContextResponse();
        context.setRuleParameters(List.of(ruleParameter(
                "loan.bo3.rate",
                "租借",
                "BO3 租借费比例",
                "0.6",
                "倍")));
        context.setRewardRules(List.of(rewardRule("BO3", "2:1", 1800, 800, null)));

        Method method = DeepSeekTeamManagerChatService.class
                .getDeclaredMethod("summarizeContext", TeamManagerDtos.ContextResponse.class, TeamManagerDtos.ChatRequest.class);
        method.setAccessible(true);
        String summary = (String) method.invoke(service, context, new TeamManagerDtos.ChatRequest());

        assertTrue(summary.contains("规则参数明细"));
        assertTrue(summary.contains("loan.bo3.rate"));
        assertTrue(summary.contains("BO3 租借费比例=0.6倍"));
        assertTrue(summary.contains("赛果奖励规则"));
        assertTrue(summary.contains("BO3 2:1"));
        assertTrue(summary.contains("胜方 1800P"));
        assertTrue(summary.contains("败方 800P"));
    }

    private RuleParameterVO ruleParameter(String key, String groupName, String name, String value, String unit) {
        RuleParameterVO parameter = new RuleParameterVO();
        parameter.setParamKey(key);
        parameter.setGroupName(groupName);
        parameter.setName(name);
        parameter.setValueText(value);
        parameter.setUnit(unit);
        parameter.setIsActive(1);
        parameter.setSortOrder(1);
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
