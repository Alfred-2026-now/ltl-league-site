package com.ltl.league.admin.controller;

import com.ltl.league.admin.dto.AdminMatchListItemVO;
import com.ltl.league.admin.dto.MatchCreateRequest;
import com.ltl.league.admin.dto.MatchUpdateRequest;
import com.ltl.league.admin.service.AdminMatchService;
import com.ltl.league.common.Result;
import com.ltl.league.entity.Match;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/matches")
public class AdminMatchController {

    private final AdminMatchService adminMatchService;

    public AdminMatchController(AdminMatchService adminMatchService) {
        this.adminMatchService = adminMatchService;
    }

    @GetMapping
    public Result<List<AdminMatchListItemVO>> list(
            @RequestParam(required = false) String season,
            @RequestParam(required = false) Integer round,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) Integer schedulePublished,
            @RequestParam(required = false) String status
    ) {
        return Result.success(adminMatchService.list(season, round, teamId, format, schedulePublished, status));
    }

    @PostMapping
    public Result<Match> create(@RequestBody MatchCreateRequest request) {
        return Result.success(adminMatchService.create(request));
    }

    @GetMapping("/{id}")
    public Result<Match> get(@PathVariable Long id) {
        return Result.success(adminMatchService.getByIdOrThrow(id));
    }

    @PutMapping("/{id}")
    public Result<Match> update(@PathVariable Long id, @RequestBody MatchUpdateRequest request) {
        return Result.success(adminMatchService.update(id, request));
    }

    @PostMapping("/{id}/publish-schedule")
    public Result<Void> publishSchedule(@PathVariable Long id) {
        adminMatchService.publishSchedule(id);
        return Result.success();
    }

    @PostMapping("/{id}/unpublish-schedule")
    public Result<Void> unpublishSchedule(@PathVariable Long id) {
        adminMatchService.unpublishSchedule(id);
        return Result.success();
    }
}

