package com.ltl.league.admin.controller;

import com.ltl.league.admin.dto.AdminPLedgerVO;
import com.ltl.league.admin.dto.AdminValuationChangeVO;
import com.ltl.league.admin.dto.ManualPLedgerRequest;
import com.ltl.league.admin.dto.ManualValuationAdjustRequest;
import com.ltl.league.admin.dto.DeductTeamPCoinsRequest;
import com.ltl.league.admin.dto.SalaryRequest;
import com.ltl.league.admin.service.AdminEconomyService;
import com.ltl.league.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @PostMapping("/p-ledger/manual-add")
    public Result<AdminPLedgerVO> manualAddPLedger(@RequestBody ManualPLedgerRequest request) {
        return Result.success(adminEconomyService.manualAddPLedger(request));
    }

    @PostMapping("/p-ledger/{ledgerId}/void")
    public Result<Void> voidPLedger(
            @PathVariable Long ledgerId,
            @RequestParam(required = false) String reason) {
        adminEconomyService.voidPLedger(ledgerId, reason);
        return Result.success();
    }

    @PostMapping("/valuation-changes/{changeId}/void")
    public Result<Void> voidValuationChange(
            @PathVariable Long changeId,
            @RequestParam(required = false) String reason) {
        adminEconomyService.voidValuationChange(changeId, reason);
        return Result.success();
    }

    @PostMapping("/p-ledger/deduct-team-pcoins")
    public Result<Void> deductTeamPCoins(@RequestBody DeductTeamPCoinsRequest request) {
        adminEconomyService.deductTeamPCoins(request);
        return Result.success();
    }

    @PostMapping("/p-ledger/deduct-all-teams-salary")
    public Result<Void> deductAllTeamsSalary(@RequestBody SalaryRequest request) {
        adminEconomyService.deductAllTeamsSalary(request.getRate());
        return Result.success();
    }

    @PostMapping("/p-ledger/deduct-all-teams-salary-void")
    public Result<Void> voidDeductAllTeamsSalary(@RequestBody(required = false) java.util.Map<String, String> payload) {
        String reason = payload != null ? payload.get("reason") : null;
        adminEconomyService.voidDeductAllTeamsSalary(reason);
        return Result.success();
    }
}
