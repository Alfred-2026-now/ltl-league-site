package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.dto.PredictionDtos;
import com.ltl.league.entity.Player;
import com.ltl.league.service.CurrentPlayerService;
import com.ltl.league.service.PredictionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class PredictionController {
    private static final String COOKIE_NAME = "ltl_auth";

    private final PredictionService predictionService;
    private final CurrentPlayerService currentPlayerService;

    public PredictionController(PredictionService predictionService, CurrentPlayerService currentPlayerService) {
        this.predictionService = predictionService;
        this.currentPlayerService = currentPlayerService;
    }

    @GetMapping("/predictions")
    public Result<List<PredictionDtos.PredictionVO>> list(
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        return Result.success(predictionService.listPublic(currentPlayerService.optional(token)));
    }

    @GetMapping("/predictions/{predictionId}")
    public Result<PredictionDtos.PredictionVO> detail(
            @PathVariable Long predictionId,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        return Result.success(predictionService.getPublicDetail(predictionId, currentPlayerService.optional(token)));
    }

    @PostMapping("/predictions/{predictionId}/bets")
    public Result<PredictionDtos.PredictionVO> placeBet(
            @PathVariable Long predictionId,
            @RequestBody PredictionDtos.BetRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player player = currentPlayerService.require(token);
        return Result.success(predictionService.placeBet(player.getId(), predictionId, request.getOptionId()));
    }
}
