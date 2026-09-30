package com.ltl.league.admin.controller;

import com.ltl.league.common.Result;
import com.ltl.league.dto.PredictionDtos;
import com.ltl.league.entity.Player;
import com.ltl.league.service.CurrentPlayerService;
import com.ltl.league.service.PredictionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminPredictionController {
    private static final String COOKIE_NAME = "ltl_auth";

    private final PredictionService predictionService;
    private final CurrentPlayerService currentPlayerService;

    public AdminPredictionController(PredictionService predictionService, CurrentPlayerService currentPlayerService) {
        this.predictionService = predictionService;
        this.currentPlayerService = currentPlayerService;
    }

    @GetMapping("/predictions")
    public Result<List<PredictionDtos.PredictionVO>> list(
            @RequestParam(required = false) String status,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        currentPlayerService.requireAdmin(token);
        return Result.success(predictionService.listAdmin(status));
    }

    @PostMapping("/predictions")
    public Result<PredictionDtos.PredictionVO> create(
            @RequestBody PredictionDtos.AdminCreateRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player admin = currentPlayerService.requireAdmin(token);
        return Result.success(predictionService.create(admin.getId(), request));
    }

    @PutMapping("/predictions/{predictionId}")
    public Result<PredictionDtos.PredictionVO> edit(
            @PathVariable Long predictionId,
            @RequestBody PredictionDtos.AdminEditRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player admin = currentPlayerService.requireAdmin(token);
        return Result.success(predictionService.edit(admin.getId(), predictionId, request));
    }

    @GetMapping("/predictions/{predictionId}/settle-preview")
    public Result<PredictionDtos.SettlePreviewVO> settlePreview(
            @PathVariable Long predictionId,
            @RequestParam Long correctOptionId,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        currentPlayerService.requireAdmin(token);
        return Result.success(predictionService.settlePreview(predictionId, correctOptionId));
    }

    @PostMapping("/predictions/{predictionId}/settle")
    public Result<PredictionDtos.PredictionVO> settle(
            @PathVariable Long predictionId,
            @RequestBody PredictionDtos.SettleRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player admin = currentPlayerService.requireAdmin(token);
        return Result.success(predictionService.settle(admin.getId(), predictionId, request));
    }

    @PostMapping("/predictions/{predictionId}/cancel")
    public Result<PredictionDtos.PredictionVO> cancel(
            @PathVariable Long predictionId,
            @RequestBody PredictionDtos.CancelRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player admin = currentPlayerService.requireAdmin(token);
        return Result.success(predictionService.cancel(admin.getId(), predictionId, request));
    }

    @PostMapping("/predictions/{predictionId}/revoke")
    public Result<PredictionDtos.PredictionVO> revoke(
            @PathVariable Long predictionId,
            @RequestBody PredictionDtos.RevokeRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player admin = currentPlayerService.requireAdmin(token);
        return Result.success(predictionService.revoke(admin.getId(), predictionId, request));
    }

    @PostMapping("/predictions/{predictionId}/delete")
    public Result<Void> delete(
            @PathVariable Long predictionId,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        currentPlayerService.requireAdmin(token);
        predictionService.delete(null, predictionId);
        return Result.success();
    }
}
