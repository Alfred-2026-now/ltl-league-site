package com.ltl.league.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.dto.RuleRequest;
import com.ltl.league.admin.service.AdminRuleService;
import com.ltl.league.entity.Rule;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.RuleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminRuleServiceImpl implements AdminRuleService {

    private final RuleMapper ruleMapper;

    public AdminRuleServiceImpl(RuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    @Override
    public List<Rule> list() {
        return ruleMapper.selectList(new LambdaQueryWrapper<Rule>()
                .eq(Rule::getDeleted, 0)
                .orderByAsc(Rule::getDisplayOrder));
    }

    @Override
    public Rule create(RuleRequest request) {
        validateRequest(request);
        Rule rule = new Rule();
        applyRequest(rule, request);
        ruleMapper.insert(rule);
        return rule;
    }

    @Override
    public Rule update(Long id, RuleRequest request) {
        Rule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "规则不存在");
        }
        applyRequest(rule, request);
        ruleMapper.updateById(rule);
        return rule;
    }

    @Override
    public void delete(Long id) {
        Rule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "规则不存在");
        }
        ruleMapper.deleteById(id);
    }

    private void validateRequest(RuleRequest request) {
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BusinessException(400, "规则标题不能为空");
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new BusinessException(400, "规则内容不能为空");
        }
    }

    private void applyRequest(Rule rule, RuleRequest request) {
        if (request.getTitle() != null) {
            rule.setTitle(request.getTitle().trim());
        }
        if (request.getContent() != null) {
            rule.setContent(request.getContent().trim());
        }
        if (request.getDisplayOrder() != null) {
            rule.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getIsOpen() != null) {
            rule.setIsOpen(request.getIsOpen());
        }
    }
}
