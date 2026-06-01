package com.ltl.league.admin.controller;

import com.ltl.league.admin.dto.*;
import com.ltl.league.admin.service.AdminPlayerDepositService;
import com.ltl.league.common.Result;
import com.ltl.league.entity.Player;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class AdminPlayerDepositController {

    private final AdminPlayerDepositService adminPlayerDepositService;

    public AdminPlayerDepositController(AdminPlayerDepositService adminPlayerDepositService) {
        this.adminPlayerDepositService = adminPlayerDepositService;
    }

    @PostMapping("/players/deposit")
    public Result<Void> adjustPlayerDeposit(@RequestBody AdjustPlayerDepositRequest request) {
        adminPlayerDepositService.adjustPlayerDeposit(request);
        return Result.success();
    }

    @PostMapping("/players")
    public Result<Player> createPlayer(@RequestBody CreatePlayerRequest request) {
        return Result.success(adminPlayerDepositService.createPlayer(request));
    }

    @PutMapping("/players/{playerId}")
    public Result<Player> updatePlayer(@PathVariable Long playerId, @RequestBody UpdatePlayerRequest request) {
        return Result.success(adminPlayerDepositService.updatePlayer(playerId, request));
    }

    @GetMapping("/players/deposit-ledgers")
    public Result<java.util.List<PlayerDepositLedgerVO>> listPlayerDepositLedgers(
            @RequestParam(required = false) Long playerId,
            @RequestParam(required = false) Integer isVoided,
            @RequestParam(required = false) Integer limit) {
        return Result.success(adminPlayerDepositService.listPlayerDepositLedgers(playerId, isVoided, limit));
    }

    @PostMapping("/players/deposit-ledgers/{ledgerId}/void")
    public Result<Void> voidPlayerDepositLedger(
            @PathVariable Long ledgerId,
            @RequestParam(required = false) String reason) {
        adminPlayerDepositService.voidPlayerDepositLedger(ledgerId, reason);
        return Result.success();
    }

    @PostMapping("/players/salary")
    public Result<Void> paySalary(@RequestBody SalaryRequest request) {
        adminPlayerDepositService.paySalary(request);
        return Result.success();
    }

    @PostMapping("/players/salary/{batchId}/void")
    public Result<Void> voidSalary(
            @PathVariable Long batchId,
            @RequestParam(required = false) String reason) {
        adminPlayerDepositService.voidSalary(batchId, reason);
        return Result.success();
    }
}
