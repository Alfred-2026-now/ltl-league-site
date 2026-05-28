package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.entity.Match;
import com.ltl.league.service.MatchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    public Result<List<Match>> getAllMatches() {
        return Result.success(matchService.getAllMatches());
    }

    @GetMapping("/{id}")
    public Result<Match> getMatchById(@PathVariable Long id) {
        Match match = matchService.getMatchById(id);
        if (match == null) {
            return Result.error("比赛不存在: " + id);
        }
        return Result.success(match);
    }
}
