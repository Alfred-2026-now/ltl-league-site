package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.dto.EventTaskDtos;
import com.ltl.league.entity.Player;
import com.ltl.league.service.CurrentPlayerService;
import com.ltl.league.service.EventTaskService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class EventTaskController {
    private static final String COOKIE_NAME = "ltl_auth";

    private final EventTaskService taskService;
    private final CurrentPlayerService currentPlayerService;

    public EventTaskController(EventTaskService taskService, CurrentPlayerService currentPlayerService) {
        this.taskService = taskService;
        this.currentPlayerService = currentPlayerService;
    }

    @GetMapping("/event-tasks/settings")
    public Result<EventTaskDtos.TaskPublicSettingsVO> settings() {
        return Result.success(taskService.getPublicSettings());
    }

    @GetMapping("/event-tasks")
    public Result<List<EventTaskDtos.TaskVO>> list(
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        return Result.success(taskService.listPublic(currentPlayerService.optional(token)));
    }

    @GetMapping("/event-tasks/{taskId}")
    public Result<EventTaskDtos.TaskVO> detail(
            @PathVariable Long taskId,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        return Result.success(taskService.getPublicDetail(taskId, currentPlayerService.optional(token)));
    }

    @GetMapping("/event-tasks/mine/published")
    public Result<List<EventTaskDtos.TaskVO>> minePublished(
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player player = currentPlayerService.require(token);
        return Result.success(taskService.listPublishedBy(player.getId()));
    }

    @GetMapping("/event-tasks/mine/claimed")
    public Result<List<EventTaskDtos.ClaimVO>> mineClaimed(
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player player = currentPlayerService.require(token);
        return Result.success(taskService.listClaimedBy(player.getId()));
    }

    @PostMapping("/event-tasks")
    public Result<EventTaskDtos.TaskVO> create(
            @RequestBody EventTaskDtos.TaskWriteRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player player = currentPlayerService.require(token);
        return Result.success(taskService.create(player.getId(), request));
    }

    @PutMapping("/event-tasks/{taskId}")
    public Result<EventTaskDtos.TaskVO> update(
            @PathVariable Long taskId,
            @RequestBody EventTaskDtos.TaskWriteRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player player = currentPlayerService.require(token);
        return Result.success(taskService.updateReturned(player.getId(), taskId, request));
    }

    @PostMapping("/event-tasks/{taskId}/resubmit")
    public Result<EventTaskDtos.TaskVO> resubmit(
            @PathVariable Long taskId,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player player = currentPlayerService.require(token);
        return Result.success(taskService.resubmit(player.getId(), taskId));
    }

    @PostMapping("/event-tasks/{taskId}/abandon-publication")
    public Result<Void> abandonPublication(
            @PathVariable Long taskId,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player player = currentPlayerService.require(token);
        taskService.abandonPublication(player.getId(), taskId);
        return Result.success();
    }

    @PostMapping("/event-tasks/{taskId}/claims")
    public Result<EventTaskDtos.ClaimVO> claim(
            @PathVariable Long taskId,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player player = currentPlayerService.require(token);
        return Result.success(taskService.claim(player.getId(), taskId));
    }

    @PostMapping("/event-task-claims/{claimId}/abandon")
    public Result<EventTaskDtos.ClaimVO> abandonClaim(
            @PathVariable Long claimId,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player player = currentPlayerService.require(token);
        return Result.success(taskService.abandonClaim(player.getId(), claimId));
    }

    @PostMapping(value = "/event-task-claims/{claimId}/proofs", consumes = "multipart/form-data")
    public Result<EventTaskDtos.ProofVO> submitProof(
            @PathVariable Long claimId,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("files") List<MultipartFile> files,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player player = currentPlayerService.require(token);
        return Result.success(taskService.submitProof(player.getId(), claimId, description, files));
    }
}
