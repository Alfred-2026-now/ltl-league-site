package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.entity.Player;
import com.ltl.league.service.PlayerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping
    public Result<List<Player>> getAllPlayers() {
        return Result.success(playerService.getAllPlayers());
    }

    @GetMapping("/team/{teamId}")
    public Result<List<Player>> getPlayersByTeamId(@PathVariable Long teamId) {
        return Result.success(playerService.getPlayersByTeamId(teamId));
    }
}
