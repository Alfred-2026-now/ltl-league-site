package com.ltl.league.admin.controller;

import com.ltl.league.admin.dto.RuleParameterHistoryVO;
import com.ltl.league.admin.dto.RuleParameterUpdateRequest;
import com.ltl.league.admin.dto.RuleParameterVO;
import com.ltl.league.admin.service.RuleParameterService;
import com.ltl.league.common.Result;
import com.ltl.league.util.AuthUtil;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/rule-parameters")
public class AdminRuleParameterController {

    private static final String COOKIE_NAME = "ltl_auth";

    private final RuleParameterService ruleParameterService;
    private final AuthUtil authUtil;

    public AdminRuleParameterController(RuleParameterService ruleParameterService, AuthUtil authUtil) {
        this.ruleParameterService = ruleParameterService;
        this.authUtil = authUtil;
    }

    @GetMapping
    public Result<List<RuleParameterVO>> list(@RequestParam(required = false) String groupKey) {
        return Result.success(ruleParameterService.listParameters(groupKey));
    }

    @PutMapping("/{key}")
    public Result<RuleParameterVO> update(
            @PathVariable String key,
            @RequestBody RuleParameterUpdateRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        return Result.success(ruleParameterService.updateParameter(key, request, operatorFromToken(token)));
    }

    @GetMapping("/history")
    public Result<List<RuleParameterHistoryVO>> history(
            @RequestParam(required = false) String groupKey,
            @RequestParam(required = false) Integer limit) {
        return Result.success(ruleParameterService.listHistory(groupKey, limit));
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
