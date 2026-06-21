package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.dto.PlayerReviewDtos;
import com.ltl.league.service.PlayerReviewServiceImpl;
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

@RestController
@RequestMapping("/player-reviews")
public class PlayerReviewController {

    private static final String COOKIE_NAME = "ltl_auth";

    private final PlayerReviewServiceImpl reviewService;
    private final AuthUtil authUtil;

    public PlayerReviewController(PlayerReviewServiceImpl reviewService, AuthUtil authUtil) {
        this.reviewService = reviewService;
        this.authUtil = authUtil;
    }

    @GetMapping("/players")
    public Result<PlayerReviewDtos.PlayerListResponse> listPlayers(
            @RequestParam(defaultValue = "reviewCount") String sort,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        AuthUtil.CookieData cookieData = parseOptional(token);
        return Result.success(reviewService.listPlayers(sort, userId(cookieData), role(cookieData)));
    }

    @GetMapping("/players/{playerId}")
    public Result<PlayerReviewDtos.PlayerDetailResponse> getPlayerReviews(
            @PathVariable Long playerId,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        AuthUtil.CookieData cookieData = parseOptional(token);
        return Result.success(reviewService.getPlayerReviews(playerId, userId(cookieData), role(cookieData)));
    }

    @PostMapping("/players/{playerId}/reviews")
    public Result<PlayerReviewDtos.ReviewVO> createReview(
            @PathVariable Long playerId,
            @RequestBody PlayerReviewDtos.CreateReviewRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        AuthUtil.CookieData cookieData = requireLogin(token);
        request.setPlayerId(playerId);
        return Result.success(reviewService.createReview(cookieData.getPlayerId(), request));
    }

    @PostMapping("/reviews/{reviewId}/ratings")
    public Result<PlayerReviewDtos.ReviewVO> rateReview(
            @PathVariable Long reviewId,
            @RequestBody PlayerReviewDtos.RatingRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        AuthUtil.CookieData cookieData = requireLogin(token);
        return Result.success(reviewService.rateReview(cookieData.getPlayerId(), reviewId, request));
    }

    @PostMapping("/reviews/{reviewId}/tips")
    public Result<PlayerReviewDtos.ReviewVO> tipReview(
            @PathVariable Long reviewId,
            @RequestBody PlayerReviewDtos.TipRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        AuthUtil.CookieData cookieData = requireLogin(token);
        return Result.success(reviewService.tipReview(cookieData.getPlayerId(), reviewId, request));
    }

    @PutMapping("/reviews/{reviewId}")
    public Result<PlayerReviewDtos.ReviewVO> updateReview(
            @PathVariable Long reviewId,
            @RequestBody PlayerReviewDtos.UpdateReviewRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        AuthUtil.CookieData cookieData = requireLogin(token);
        return Result.success(reviewService.updateReview(cookieData.getPlayerId(), cookieData.getRole(), reviewId, request));
    }

    @DeleteMapping("/reviews/{reviewId}")
    public Result<Void> deleteReview(
            @PathVariable Long reviewId,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        AuthUtil.CookieData cookieData = requireLogin(token);
        reviewService.deleteReview(cookieData.getPlayerId(), cookieData.getRole(), reviewId);
        return Result.success();
    }

    private AuthUtil.CookieData parseOptional(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return authUtil.parseCookieValue(token);
    }

    private AuthUtil.CookieData requireLogin(String token) {
        AuthUtil.CookieData cookieData = parseOptional(token);
        if (cookieData == null) {
            throw new BusinessException(401, "请先登录后再操作");
        }
        return cookieData;
    }

    private Long userId(AuthUtil.CookieData cookieData) {
        return cookieData == null ? null : cookieData.getPlayerId();
    }

    private Integer role(AuthUtil.CookieData cookieData) {
        return cookieData == null ? null : cookieData.getRole();
    }
}
