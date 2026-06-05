package com.ltl.league.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.dto.RewardRuleRequest;
import com.ltl.league.admin.dto.RewardRuleVO;
import com.ltl.league.admin.service.AdminRewardRuleService;
import com.ltl.league.admin.service.RuleParameterCatalog;
import com.ltl.league.admin.service.RuleParameterService;
import com.ltl.league.entity.SettlementRewardRule;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.SettlementRewardRuleMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminRewardRuleServiceImpl implements AdminRewardRuleService {

    private final SettlementRewardRuleMapper rewardRuleMapper;
    private final JdbcTemplate jdbcTemplate;
    private final RuleParameterService ruleParameterService;

    public AdminRewardRuleServiceImpl(
            SettlementRewardRuleMapper rewardRuleMapper,
            JdbcTemplate jdbcTemplate,
            RuleParameterService ruleParameterService) {
        this.rewardRuleMapper = rewardRuleMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.ruleParameterService = ruleParameterService;
    }

    @Override
    public List<RewardRuleVO> list(String format) {
        LambdaQueryWrapper<SettlementRewardRule> query = new LambdaQueryWrapper<SettlementRewardRule>()
                .eq(SettlementRewardRule::getDeleted, 0)
                .orderByAsc(SettlementRewardRule::getFormat)
                .orderByAsc(SettlementRewardRule::getScorePattern);
        if (format != null && !format.isBlank()) {
            query.eq(SettlementRewardRule::getFormat, format);
        }
        return rewardRuleMapper.selectList(query).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public RewardRuleVO create(RewardRuleRequest request, String operator) {
        validateRequest(request);
        SettlementRewardRule rule = new SettlementRewardRule();
        applyRequest(rule, request);
        rewardRuleMapper.insert(rule);
        logRewardHistory("新增", null, rule, operator, request.getChangeReason());
        return toVO(rule);
    }

    @Override
    public RewardRuleVO update(Long id, RewardRuleRequest request, String operator) {
        SettlementRewardRule rule = rewardRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "规则不存在");
        }
        SettlementRewardRule before = copy(rule);
        applyRequest(rule, request);
        rewardRuleMapper.updateById(rule);
        logRewardHistory("修改", before, rule, operator, request.getChangeReason());
        return toVO(rule);
    }

    @Override
    public void delete(Long id, String operator, String reason) {
        SettlementRewardRule rule = rewardRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "规则不存在");
        }
        logRewardHistory("删除", rule, null, operator, reason);
        jdbcTemplate.update("DELETE FROM settlement_reward_rules WHERE id = ?", id);
    }

    private void validateRequest(RewardRuleRequest request) {
        if (request.getFormat() == null || request.getFormat().isBlank()) {
            throw new BusinessException(400, "赛制不能为空");
        }
        if (request.getScorePattern() == null || request.getScorePattern().isBlank()) {
            throw new BusinessException(400, "比分模式不能为空");
        }
    }

    private void applyRequest(SettlementRewardRule rule, RewardRuleRequest request) {
        if (request.getFormat() != null && !request.getFormat().isBlank()) {
            rule.setFormat(request.getFormat().trim().toUpperCase());
        }
        if (request.getScorePattern() != null && !request.getScorePattern().isBlank()) {
            rule.setScorePattern(request.getScorePattern().trim());
        }
        rule.setWinnerAmount(request.getWinnerAmount());
        rule.setLoserAmount(request.getLoserAmount());
        rule.setDrawAmount(request.getDrawAmount());
        rule.setIsActive(request.getIsActive() != null ? request.getIsActive() : 1);
    }

    private void logRewardHistory(String action, SettlementRewardRule before, SettlementRewardRule after, String operator, String reason) {
        SettlementRewardRule current = after != null ? after : before;
        String format = current.getFormat();
        String score = current.getScorePattern();
        ruleParameterService.recordHistory(
                RuleParameterCatalog.GROUP_REWARD,
                "比赛奖励",
                "reward." + format + "." + score,
                format + " " + score + " 比赛奖励",
                before == null ? "-" : rewardSummary(before),
                after == null ? "-" : rewardSummary(after),
                operator,
                reason == null || reason.isBlank() ? action + "比赛奖励规则" : reason);
    }

    private String rewardSummary(SettlementRewardRule rule) {
        return "胜方=" + valueOrDash(rule.getWinnerAmount())
                + "，败方=" + valueOrDash(rule.getLoserAmount())
                + "，平局=" + valueOrDash(rule.getDrawAmount())
                + "，状态=" + (rule.getIsActive() != null && rule.getIsActive() == 1 ? "启用" : "停用");
    }

    private String valueOrDash(Integer value) {
        return value == null ? "-" : value + "P";
    }

    private SettlementRewardRule copy(SettlementRewardRule source) {
        SettlementRewardRule copy = new SettlementRewardRule();
        copy.setId(source.getId());
        copy.setFormat(source.getFormat());
        copy.setScorePattern(source.getScorePattern());
        copy.setWinnerAmount(source.getWinnerAmount());
        copy.setLoserAmount(source.getLoserAmount());
        copy.setDrawAmount(source.getDrawAmount());
        copy.setIsActive(source.getIsActive());
        return copy;
    }

    private RewardRuleVO toVO(SettlementRewardRule rule) {
        RewardRuleVO vo = new RewardRuleVO();
        vo.setId(rule.getId());
        vo.setFormat(rule.getFormat());
        vo.setScorePattern(rule.getScorePattern());
        vo.setWinnerAmount(rule.getWinnerAmount());
        vo.setLoserAmount(rule.getLoserAmount());
        vo.setDrawAmount(rule.getDrawAmount());
        vo.setIsActive(rule.getIsActive());
        vo.setCreatedAt(rule.getCreatedAt() != null ? rule.getCreatedAt().toString() : null);
        vo.setUpdatedAt(rule.getUpdatedAt() != null ? rule.getUpdatedAt().toString() : null);
        return vo;
    }
}
