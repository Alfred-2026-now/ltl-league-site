package com.ltl.league.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ltl.league.entity.Rule;
import com.ltl.league.mapper.RuleMapper;
import com.ltl.league.service.RuleService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RuleServiceImpl extends ServiceImpl<RuleMapper, Rule> implements RuleService {

    @Override
    public List<Rule> getAllRules() {
        return lambdaQuery()
                .orderByAsc(Rule::getDisplayOrder)
                .list();
    }
}
