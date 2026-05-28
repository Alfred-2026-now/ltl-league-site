package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.entity.Rule;
import com.ltl.league.service.RuleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public Result<List<Rule>> getAllRules() {
        return Result.success(ruleService.getAllRules());
    }
}
