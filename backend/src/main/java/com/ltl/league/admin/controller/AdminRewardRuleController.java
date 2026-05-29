package com.ltl.league.admin.controller;

import com.ltl.league.admin.dto.RewardRuleRequest;
import com.ltl.league.admin.dto.RewardRuleVO;
import com.ltl.league.admin.service.AdminRewardRuleService;
import com.ltl.league.common.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/reward-rules")
public class AdminRewardRuleController {

    private final AdminRewardRuleService adminRewardRuleService;

    public AdminRewardRuleController(AdminRewardRuleService adminRewardRuleService) {
        this.adminRewardRuleService = adminRewardRuleService;
    }

    @GetMapping
    public Result<List<RewardRuleVO>> list(@RequestParam(required = false) String format) {
        return Result.success(adminRewardRuleService.list(format));
    }

    @PostMapping
    public Result<RewardRuleVO> create(@RequestBody RewardRuleRequest request) {
        return Result.success(adminRewardRuleService.create(request));
    }

    @PutMapping("/{id}")
    public Result<RewardRuleVO> update(@PathVariable Long id, @RequestBody RewardRuleRequest request) {
        return Result.success(adminRewardRuleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminRewardRuleService.delete(id);
        return Result.success();
    }
}
