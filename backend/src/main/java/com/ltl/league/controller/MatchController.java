package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.dto.MatchVO;
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
    public Result<List<MatchVO>> getAllMatches() {
        return Result.success(matchService.getAllMatchVOs());
    }

    @GetMapping("/{id}")
    public Result<MatchVO> getMatchById(@PathVariable Long id) {
        MatchVO matchVO = matchService.getMatchVOById(id);
        if (matchVO == null) {
            return Result.error("比赛不存在: " + id);
        }
        return Result.success(matchVO);
    }
}
