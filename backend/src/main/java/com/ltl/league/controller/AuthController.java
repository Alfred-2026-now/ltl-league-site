package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.dto.LoginRequest;
import com.ltl.league.dto.LoginResponse;
import com.ltl.league.service.AuthService;
import com.ltl.league.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthUtil authUtil;

    private static final String COOKIE_NAME = "ltl_auth";

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        try {
            LoginResponse loginResponse = authService.login(request);

            // 生成 Cookie 值
            String cookieValue = authUtil.generateCookieValue(
                    loginResponse.getPlayerId(),
                    loginResponse.getPlayerName(),
                    loginResponse.getRole()
            );

            // 设置 Cookie
            Cookie cookie = new Cookie(COOKIE_NAME, cookieValue);
            cookie.setMaxAge(authUtil.getCookieMaxAge());
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            response.addCookie(cookie);

            return Result.success(loginResponse);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("登录失败", e);
            return Result.error("登录失败：" + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        authService.logout();

        // 清除 Cookie
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);

        return Result.success();
    }

    @GetMapping("/current")
    public Result<LoginResponse> getCurrentUser(@CookieValue(value = COOKIE_NAME, required = false) String token) {
        if (token == null || token.isEmpty()) {
            return Result.error(401, "未登录");
        }

        LoginResponse user = authService.getCurrentUser(token);
        if (user == null) {
            return Result.error(401, "登录已过期");
        }

        return Result.success(user);
    }
}
