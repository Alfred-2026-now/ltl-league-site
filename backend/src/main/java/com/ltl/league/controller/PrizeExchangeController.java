package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.dto.ExchangePrizeRequest;
import com.ltl.league.dto.PrizeExchangeVO;
import com.ltl.league.service.PrizeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/prize-exchanges")
public class PrizeExchangeController {

    private final PrizeService prizeService;

    public PrizeExchangeController(PrizeService prizeService) {
        this.prizeService = prizeService;
    }

    @PostMapping
    public Result<PrizeExchangeVO> exchangePrize(@RequestBody ExchangePrizeRequest request) {
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
