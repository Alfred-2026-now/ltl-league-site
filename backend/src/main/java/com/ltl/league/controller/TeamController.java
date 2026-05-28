package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.entity.Team;
import com.ltl.league.service.TeamService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public Result<List<Team>> getAllTeams() {
        return Result.success(teamService.getAllTeams());
    }

    @GetMapping("/{state}")
    public Result<Team> getTeamByState(@PathVariable String state) {
        Team team = teamService.getByState(state);
        if (team == null) {
            return Result.error("队伍不存在: " + state);
        }
        return Result.success(team);
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("LTL League Backend is running!");
    }
}
