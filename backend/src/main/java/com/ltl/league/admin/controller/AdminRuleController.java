package com.ltl.league.admin.controller;

import com.ltl.league.admin.dto.RuleRequest;
import com.ltl.league.admin.service.AdminRuleService;
import com.ltl.league.common.Result;
import com.ltl.league.entity.Rule;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/rules")
public class AdminRuleController {

    private final AdminRuleService adminRuleService;

    public AdminRuleController(AdminRuleService adminRuleService) {
        this.adminRuleService = adminRuleService;
    }

    @GetMapping
    public Result<List<Rule>> list() {
        return Result.success(adminRuleService.list());
    }

    @PostMapping
    public Result<Rule> create(@RequestBody RuleRequest request) {
        return Result.success(adminRuleService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Rule> update(@PathVariable Long id, @RequestBody RuleRequest request) {
        return Result.success(adminRuleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminRuleService.delete(id);
        return Result.success();
    }
}
