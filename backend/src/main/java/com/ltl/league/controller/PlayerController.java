package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.dto.PublicPlayerVO;
import com.ltl.league.service.PlayerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping
    public Result<List<PublicPlayerVO>> getAllPlayers() {
        return Result.success(playerService.getAllPlayers().stream()
                .map(PublicPlayerVO::from).collect(Collectors.toList()));
    }

    @GetMapping("/team/{teamId}")
    public Result<List<PublicPlayerVO>> getPlayersByTeamId(@PathVariable Long teamId) {
        return Result.success(playerService.getPlayersByTeamId(teamId).stream()
                .map(PublicPlayerVO::from).collect(Collectors.toList()));
    }
}
