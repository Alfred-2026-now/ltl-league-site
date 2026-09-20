package com.ltl.league.admin.controller;

import com.ltl.league.common.Result;
import com.ltl.league.dto.EventTaskDtos;
import com.ltl.league.entity.Player;
import com.ltl.league.service.CurrentPlayerService;
import com.ltl.league.service.EventTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminEventTaskController {
    private static final String COOKIE_NAME = "ltl_auth";

    private final EventTaskService taskService;
    private final CurrentPlayerService currentPlayerService;

    public AdminEventTaskController(EventTaskService taskService, CurrentPlayerService currentPlayerService) {
        this.taskService = taskService;
        this.currentPlayerService = currentPlayerService;
    }

    @GetMapping("/event-tasks")
    public Result<List<EventTaskDtos.TaskVO>> list(
            @RequestParam(required = false) String status,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player admin = currentPlayerService.requireAdmin(token);
        return Result.success(taskService.listAdminTasks(status, admin));
    }

    @GetMapping("/event-task-proofs/pending")
    public Result<List<EventTaskDtos.ProofVO>> pendingProofs(
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        currentPlayerService.requireAdmin(token);
        return Result.success(taskService.listPendingProofs());
    }

    @PostMapping("/event-tasks/{taskId}/return")
    public Result<Void> returnTask(
            @PathVariable Long taskId,
            @RequestBody EventTaskDtos.AdminReturnRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player admin = currentPlayerService.requireAdmin(token);
        taskService.returnTask(admin.getId(), taskId, request);
        return Result.success();
    }

    @PostMapping("/event-tasks/{taskId}/publish")
    public Result<EventTaskDtos.TaskVO> publish(
            @PathVariable Long taskId,
            @RequestBody EventTaskDtos.AdminPublishRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player admin = currentPlayerService.requireAdmin(token);
        return Result.success(taskService.publishTask(admin.getId(), taskId, request));
    }

    @PostMapping("/event-tasks/official")
    public Result<EventTaskDtos.TaskVO> official(
            @RequestBody EventTaskDtos.OfficialTaskRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player admin = currentPlayerService.requireAdmin(token);
        return Result.success(taskService.publishOfficial(admin.getId(), request));
    }

    @PostMapping("/event-task-proofs/{proofId}/return")
    public Result<Void> returnProof(
            @PathVariable Long proofId,
            @RequestBody EventTaskDtos.ProofReviewRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player admin = currentPlayerService.requireAdmin(token);
        taskService.returnProof(admin.getId(), proofId, request);
        return Result.success();
    }

    @PostMapping("/event-task-proofs/{proofId}/approve")
    public Result<EventTaskDtos.ClaimVO> approveProof(
            @PathVariable Long proofId,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player admin = currentPlayerService.requireAdmin(token);
        return Result.success(taskService.approveProof(admin.getId(), proofId));
    }

    @PostMapping("/event-task-claims/{claimId}/revoke-completion")
    public Result<EventTaskDtos.ClaimVO> revokeCompletion(
            @PathVariable Long claimId,
            @RequestBody EventTaskDtos.CompletionRevokeRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player admin = currentPlayerService.requireAdmin(token);
        return Result.success(taskService.revokeCompletion(admin.getId(), claimId, request));
    }

    @PostMapping("/event-tasks/{taskId}/close")
    public Result<Void> close(
            @PathVariable Long taskId,
            @RequestBody EventTaskDtos.CloseTaskRequest request,
            @CookieValue(value = COOKIE_NAME, required = false) String token) {
        Player admin = currentPlayerService.requireAdmin(token);
        taskService.closeTask(admin.getId(), taskId, request);
        return Result.success();
    }
}
