package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.dto.ExchangePrizeRequest;
import com.ltl.league.dto.PrizeExchangeVO;
import com.ltl.league.service.PrizeService;
import com.ltl.league.util.AuthUtil;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/prize-exchanges")
public class PrizeExchangeController {

    private final PrizeService prizeService;
    private final AuthUtil authUtil;

    private static final String COOKIE_NAME = "ltl_auth";

    public PrizeExchangeController(PrizeService prizeService, AuthUtil authUtil) {
        this.prizeService = prizeService;
        this.authUtil = authUtil;
    }

    @PostMapping
    public Result<PrizeExchangeVO> exchangePrize(
            @Valid @RequestBody ExchangePrizeRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        // 如果提供了 Cookie，使用 Cookie 中的用户名
        if (token != null && !token.isEmpty()) {
            AuthUtil.CookieData cookieData = authUtil.parseCookieValue(token);
            if (cookieData != null) {
                request.setPlayerName(cookieData.getPlayerName());
            }
        }
        return Result.success(prizeService.exchangePrize(request));
    }

    @GetMapping
    public Result<List<PrizeExchangeVO>> listExchanges(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {
        return Result.success(prizeService.listExchanges(status, limit));
    }

    @GetMapping("/{id}")
    public Result<PrizeExchangeVO> getExchange(@PathVariable Long id) {
        return Result.success(prizeService.getExchange(id));
    }

    @PostMapping("/{id}/process")
    public Result<Void> processExchange(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "admin") String processedBy) {
        prizeService.processExchange(id, processedBy);
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancelExchange(
            @PathVariable Long id,
            @RequestParam String reason) {
        prizeService.cancelExchange(id, reason);
        return Result.success();
    }
}
