package com.ltl.league.admin.controller;

import com.ltl.league.admin.dto.AdminPLedgerVO;
import com.ltl.league.admin.dto.AdminValuationChangeVO;
import com.ltl.league.admin.dto.ManualValuationAdjustRequest;
import com.ltl.league.admin.service.AdminEconomyService;
import com.ltl.league.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminEconomyController {

    private final AdminEconomyService adminEconomyService;

    public AdminEconomyController(AdminEconomyService adminEconomyService) {
        this.adminEconomyService = adminEconomyService;
    }

    @GetMapping("/p-ledger")
    public Result<List<AdminPLedgerVO>> listPLedgers(
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long matchId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer isVoided,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer limit) {
        return Result.success(adminEconomyService.listPLedgers(teamId, matchId, type, isVoided, source, limit));
    }

    @GetMapping("/valuation-changes")
    public Result<List<AdminValuationChangeVO>> listValuationChanges(
            @RequestParam(required = false) Long playerId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long matchId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer isVoided,
            @RequestParam(required = false) Integer limit) {
        return Result.success(adminEconomyService.listValuationChanges(playerId, teamId, matchId, source, isVoided, limit));
    }

    @PostMapping("/valuation-changes/manual-adjustment")
    public Result<AdminValuationChangeVO> manualAdjustment(@RequestBody ManualValuationAdjustRequest request) {
        return Result.success(adminEconomyService.manualAdjustment(request));
    }
}
