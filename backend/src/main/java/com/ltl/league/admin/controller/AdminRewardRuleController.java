package com.ltl.league.admin.controller;

import com.ltl.league.admin.dto.RewardRuleRequest;
import com.ltl.league.admin.dto.RewardRuleVO;
import com.ltl.league.admin.service.AdminRewardRuleService;
import com.ltl.league.common.Result;
import com.ltl.league.util.AuthUtil;
import org.springframework.web.bind.annotation.CookieValue;
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

    private static final String COOKIE_NAME = "ltl_auth";

    private final AdminRewardRuleService adminRewardRuleService;
    private final AuthUtil authUtil;

    public AdminRewardRuleController(AdminRewardRuleService adminRewardRuleService, AuthUtil authUtil) {
        this.adminRewardRuleService = adminRewardRuleService;
        this.authUtil = authUtil;
    }

    @GetMapping
    public Result<List<RewardRuleVO>> list(@RequestParam(required = false) String format) {
        return Result.success(adminRewardRuleService.list(format));
    }

    @PostMapping
    public Result<RewardRuleVO> create(
            @RequestBody RewardRuleRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        return Result.success(adminRewardRuleService.create(request, operatorFromToken(token)));
    }

    @PutMapping("/{id}")
    public Result<RewardRuleVO> update(
            @PathVariable Long id,
            @RequestBody RewardRuleRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        return Result.success(adminRewardRuleService.update(id, request, operatorFromToken(token)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        adminRewardRuleService.delete(id, operatorFromToken(token), reason);
        return Result.success();
    }

    private String operatorFromToken(String token) {
        if (token == null || token.isBlank()) {
            return "admin";
        }
        AuthUtil.CookieData cookieData = authUtil.parseCookieValue(token);
        if (cookieData == null || cookieData.getPlayerName() == null || cookieData.getPlayerName().isBlank()) {
            return "admin";
        }
        return cookieData.getPlayerName();
    }
}
