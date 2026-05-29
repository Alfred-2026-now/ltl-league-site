package com.ltl.league.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.dto.RewardRuleRequest;
import com.ltl.league.admin.dto.RewardRuleVO;
import com.ltl.league.admin.service.AdminRewardRuleService;
import com.ltl.league.entity.SettlementRewardRule;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.SettlementRewardRuleMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminRewardRuleServiceImpl implements AdminRewardRuleService {

    private final SettlementRewardRuleMapper rewardRuleMapper;

    public AdminRewardRuleServiceImpl(SettlementRewardRuleMapper rewardRuleMapper) {
        this.rewardRuleMapper = rewardRuleMapper;
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
    public RewardRuleVO create(RewardRuleRequest request) {
        validateRequest(request);
        SettlementRewardRule rule = new SettlementRewardRule();
        applyRequest(rule, request);
        rewardRuleMapper.insert(rule);
        return toVO(rule);
    }

    @Override
    public RewardRuleVO update(Long id, RewardRuleRequest request) {
        SettlementRewardRule rule = rewardRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "规则不存在");
        }
        applyRequest(rule, request);
        rewardRuleMapper.updateById(rule);
        return toVO(rule);
    }

    @Override
    public void delete(Long id) {
        SettlementRewardRule rule = rewardRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "规则不存在");
        }
        rewardRuleMapper.deleteById(id);
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
        rule.setFormat(request.getFormat().trim().toUpperCase());
        rule.setScorePattern(request.getScorePattern().trim());
        rule.setWinnerAmount(request.getWinnerAmount());
        rule.setLoserAmount(request.getLoserAmount());
        rule.setDrawAmount(request.getDrawAmount());
        rule.setIsActive(request.getIsActive() != null ? request.getIsActive() : 1);
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
