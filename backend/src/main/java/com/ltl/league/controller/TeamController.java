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
    public Result<List<Team>> getAllTeams(@RequestParam(value = "season", required = false) String season) {
        // season=all 返回全部赛季战队（admin 历史 id→名字映射场景）
        // 不传或其他值返回当前赛季战队（公开战队列表）
        if ("all".equalsIgnoreCase(season)) {
            return Result.success(teamService.getAllTeams());
        }
        return Result.success(teamService.getCurrentSeasonTeams());
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
