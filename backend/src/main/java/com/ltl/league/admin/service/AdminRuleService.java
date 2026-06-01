package com.ltl.league.admin.service;

import com.ltl.league.admin.dto.RuleRequest;
import com.ltl.league.entity.Rule;

import java.util.List;

public interface AdminRuleService {
    List<Rule> list();
    Rule create(RuleRequest request);
    Rule update(Long id, RuleRequest request);
    void delete(Long id);
}
