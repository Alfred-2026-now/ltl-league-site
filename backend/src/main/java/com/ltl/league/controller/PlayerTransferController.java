package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.dto.PlayerTransferPreviewVO;
import com.ltl.league.dto.PlayerTransferRequest;
import com.ltl.league.dto.PlayerTransferVO;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.service.PlayerTransferService;
import com.ltl.league.util.AuthUtil;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/player-transfers")
public class PlayerTransferController {

    private static final String COOKIE_NAME = "ltl_auth";

    private final PlayerTransferService transferService;
    private final AuthUtil authUtil;

    public PlayerTransferController(PlayerTransferService transferService, AuthUtil authUtil) {
        this.transferService = transferService;
        this.authUtil = authUtil;
    }

    @PostMapping("/preview")
    public Result<PlayerTransferPreviewVO> preview(
            @CookieValue(value = COOKIE_NAME, required = false) String token,
            @RequestBody PlayerTransferRequest request) {
        Long playerId = requireLogin(token);
        return Result.success(transferService.preview(playerId, request));
    }

    @PostMapping
    public Result<PlayerTransferVO> transfer(
            @CookieValue(value = COOKIE_NAME, required = false) String token,
            @RequestBody PlayerTransferRequest request) {
        Long playerId = requireLogin(token);
        return Result.success(transferService.transfer(playerId, request));
    }

    @GetMapping("/my")
    public Result<List<PlayerTransferVO>> listMine(
            @CookieValue(value = COOKIE_NAME, required = false) String token,
            @RequestParam(required = false) Integer limit) {
        Long playerId = requireLogin(token);
        return Result.success(transferService.listMine(playerId, limit));
    }

    private Long requireLogin(String token) {
        if (token == null || token.isEmpty()) {
            throw new BusinessException(401, "未登录");
        }
        AuthUtil.CookieData cookieData = authUtil.parseCookieValue(token);
        if (cookieData == null) {
            throw new BusinessException(401, "登录已过期");
        }
        return cookieData.getPlayerId();
    }
}
