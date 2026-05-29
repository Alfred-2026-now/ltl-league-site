package com.ltl.league.admin.controller;

import com.ltl.league.admin.dto.MatchResultAttachmentVO;
import com.ltl.league.admin.dto.MatchResultDraftRequest;
import com.ltl.league.admin.dto.MatchResultVO;
import com.ltl.league.admin.dto.MatchResultWithdrawRequest;
import com.ltl.league.admin.dto.SettlementPreviewVO;
import com.ltl.league.admin.service.AdminMatchResultService;
import com.ltl.league.common.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin")
public class AdminMatchResultController {

    private final AdminMatchResultService adminMatchResultService;

    public AdminMatchResultController(AdminMatchResultService adminMatchResultService) {
        this.adminMatchResultService = adminMatchResultService;
    }

    @GetMapping("/matches/{matchId}/result")
    public Result<MatchResultVO> getResult(@PathVariable Long matchId) {
        return Result.success(adminMatchResultService.getResultContext(matchId));
    }

    @PostMapping("/matches/{matchId}/result/draft")
    public Result<MatchResultVO> createDraft(@PathVariable Long matchId, @RequestBody MatchResultDraftRequest request) {
        return Result.success(adminMatchResultService.createDraft(matchId, request));
    }

    @PutMapping("/matches/{matchId}/result/draft/{resultId}")
    public Result<MatchResultVO> updateDraft(
            @PathVariable Long matchId,
            @PathVariable Long resultId,
            @RequestBody MatchResultDraftRequest request) {
        return Result.success(adminMatchResultService.updateDraft(matchId, resultId, request));
    }

    @PostMapping("/matches/{matchId}/result/settlement-preview")
    public Result<SettlementPreviewVO> settlementPreview(
            @PathVariable Long matchId,
            @RequestBody MatchResultDraftRequest request) {
        return Result.success(adminMatchResultService.previewSettlement(matchId, request));
    }

    @PostMapping("/matches/{matchId}/result/{resultId}/publish")
    public Result<Void> publish(@PathVariable Long matchId, @PathVariable Long resultId) {
        adminMatchResultService.publish(matchId, resultId);
        return Result.success();
    }

    @PostMapping("/matches/{matchId}/result/{resultId}/withdraw")
    public Result<MatchResultVO> withdraw(
            @PathVariable Long matchId,
            @PathVariable Long resultId,
            @RequestBody MatchResultWithdrawRequest request) {
        return Result.success(adminMatchResultService.withdraw(
                matchId, resultId, request != null ? request.getWithdrawReason() : null));
    }

    @PostMapping("/matches/{matchId}/result/{resultId}/games/{gameIndex}/screenshots")
    public Result<MatchResultAttachmentVO> uploadScreenshot(
            @PathVariable Long matchId,
            @PathVariable Long resultId,
            @PathVariable Integer gameIndex,
            @RequestParam("file") MultipartFile file) {
        return Result.success(adminMatchResultService.uploadScreenshot(matchId, resultId, gameIndex, file));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public Result<Void> deleteAttachment(@PathVariable Long attachmentId) {
        adminMatchResultService.deleteAttachment(attachmentId);
        return Result.success();
    }
}
