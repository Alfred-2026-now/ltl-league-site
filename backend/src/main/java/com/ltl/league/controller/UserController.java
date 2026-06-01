package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.dto.ChangePasswordRequest;
import com.ltl.league.dto.PrizeExchangeDetailVO;
import com.ltl.league.dto.UserInfoVO;
import com.ltl.league.service.UserService;
import com.ltl.league.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthUtil authUtil;

    private static final String COOKIE_NAME = "ltl_auth";

    @GetMapping("/info")
    public Result<UserInfoVO> getUserInfo(@CookieValue(value = COOKIE_NAME, required = false) String token) {
        if (token == null || token.isEmpty()) {
            return Result.error(401, "未登录");
        }

        AuthUtil.CookieData cookieData = authUtil.parseCookieValue(token);
        if (cookieData == null) {
            return Result.error(401, "登录已过期");
        }

        try {
            UserInfoVO userInfo = userService.getUserInfo(cookieData.getPlayerId());
            return Result.success(userInfo);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/prize-exchanges")
    public Result<List<PrizeExchangeDetailVO>> getUserPrizeExchanges(
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        if (token == null || token.isEmpty()) {
            return Result.error(401, "未登录");
        }

        AuthUtil.CookieData cookieData = authUtil.parseCookieValue(token);
        if (cookieData == null) {
            return Result.error(401, "登录已过期");
        }

        List<PrizeExchangeDetailVO> exchanges = userService.getUserPrizeExchanges(cookieData.getPlayerId());
        return Result.success(exchanges);
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(
            @RequestBody ChangePasswordRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        if (token == null || token.isEmpty()) {
            return Result.error(401, "未登录");
        }

        AuthUtil.CookieData cookieData = authUtil.parseCookieValue(token);
        if (cookieData == null) {
            return Result.error(401, "登录已过期");
        }

        try {
            userService.changePassword(cookieData.getPlayerId(), request);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
}
