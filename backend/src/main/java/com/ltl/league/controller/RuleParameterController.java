package com.ltl.league.controller;

import com.ltl.league.admin.dto.RuleParameterVO;
import com.ltl.league.admin.service.RuleParameterService;
import com.ltl.league.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/rule-parameters")
public class RuleParameterController {

    private final RuleParameterService ruleParameterService;

    public RuleParameterController(RuleParameterService ruleParameterService) {
        this.ruleParameterService = ruleParameterService;
    }

    @GetMapping
    public Result<List<RuleParameterVO>> list(@RequestParam(required = false) String groupKey) {
        return Result.success(ruleParameterService.listParameters(groupKey));
    }
}
