package com.ltl.league.admin.controller;

import com.ltl.league.admin.dto.*;
import com.ltl.league.admin.service.AdminPlayerDepositService;
import com.ltl.league.common.Result;
import com.ltl.league.entity.Player;
import com.ltl.league.service.CurrentPlayerService;
import com.ltl.league.service.PlayerService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class AdminPlayerDepositController {

    private final AdminPlayerDepositService adminPlayerDepositService;
    private final CurrentPlayerService currentPlayerService;
    private final PlayerService playerService;

    public AdminPlayerDepositController(AdminPlayerDepositService adminPlayerDepositService,
                                        CurrentPlayerService currentPlayerService,
                                        PlayerService playerService) {
        this.adminPlayerDepositService = adminPlayerDepositService;
        this.currentPlayerService = currentPlayerService;
        this.playerService = playerService;
    }

    @GetMapping("/players")
    public Result<java.util.List<Player>> listPlayers(
            @CookieValue(value = "ltl_auth", required = false) String token) {
        currentPlayerService.requireAdmin(token);
        return Result.success(playerService.getAllPlayers());
    }

    @PostMapping("/players/deposit")
    public Result<Void> adjustPlayerDeposit(@RequestBody AdjustPlayerDepositRequest request,
                                           @CookieValue(value = "ltl_auth", required = false) String token) {
        currentPlayerService.requireAdmin(token);
        adminPlayerDepositService.adjustPlayerDeposit(request);
        return Result.success();
    }

    @PostMapping("/players/bounty")
    public Result<Void> adjustPlayerBounty(@RequestBody AdjustPlayerBountyRequest request,
                                          @CookieValue(value = "ltl_auth", required = false) String token) {
        currentPlayerService.requireAdmin(token);
        adminPlayerDepositService.adjustPlayerBounty(request);
        return Result.success();
    }

    @PostMapping("/players")
    public Result<Player> createPlayer(@RequestBody CreatePlayerRequest request,
                                       @CookieValue(value = "ltl_auth", required = false) String token) {
        currentPlayerService.requireAdmin(token);
        return Result.success(adminPlayerDepositService.createPlayer(request));
    }

    @PutMapping("/players/{playerId}")
    public Result<Player> updatePlayer(@PathVariable Long playerId, @RequestBody UpdatePlayerRequest request,
                                       @CookieValue(value = "ltl_auth", required = false) String token) {
        currentPlayerService.requireAdmin(token);
        return Result.success(adminPlayerDepositService.updatePlayer(playerId, request));
    }

    @PostMapping("/players/{playerId}/position-active")
    public Result<Player> setPositionActive(@PathVariable Long playerId,
                                           @RequestBody SetPositionActiveRequest request,
                                           @CookieValue(value = "ltl_auth", required = false) String token) {
        currentPlayerService.requireAdmin(token);
        return Result.success(adminPlayerDepositService.setPositionActive(playerId, request));
    }

    @DeleteMapping("/players/{playerId}")
    public Result<Void> deletePlayer(@PathVariable Long playerId,
                                    @CookieValue(value = "ltl_auth", required = false) String token) {
        currentPlayerService.requireAdmin(token);
        adminPlayerDepositService.deletePlayer(playerId);
        return Result.success();
    }

    @GetMapping("/players/deposit-ledgers")
    public Result<java.util.List<PlayerDepositLedgerVO>> listPlayerDepositLedgers(
            @RequestParam(required = false) Long playerId,
            @RequestParam(required = false) Integer isVoided,
            @RequestParam(required = false) Integer limit,
            @CookieValue(value = "ltl_auth", required = false) String token) {
        currentPlayerService.requireAdmin(token);
        return Result.success(adminPlayerDepositService.listPlayerDepositLedgers(playerId, isVoided, limit));
    }

    @PostMapping("/players/deposit-ledgers/{ledgerId}/void")
    public Result<Void> voidPlayerDepositLedger(
            @PathVariable Long ledgerId,
            @RequestParam(required = false) String reason,
            @CookieValue(value = "ltl_auth", required = false) String token) {
        currentPlayerService.requireAdmin(token);
        adminPlayerDepositService.voidPlayerDepositLedger(ledgerId, reason);
        return Result.success();
    }

    @PostMapping("/players/salary")
    public Result<Void> paySalary(@RequestBody SalaryRequest request,
                                  @CookieValue(value = "ltl_auth", required = false) String token) {
        currentPlayerService.requireAdmin(token);
        adminPlayerDepositService.paySalary(request);
        return Result.success();
    }

    @PostMapping("/players/salary/{batchId}/void")
    public Result<Void> voidSalary(
            @PathVariable Long batchId,
            @RequestParam(required = false) String reason,
            @CookieValue(value = "ltl_auth", required = false) String token) {
        currentPlayerService.requireAdmin(token);
        adminPlayerDepositService.voidSalary(batchId, reason);
        return Result.success();
    }
}
