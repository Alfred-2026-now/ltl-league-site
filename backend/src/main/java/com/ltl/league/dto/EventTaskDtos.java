package com.ltl.league.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public final class EventTaskDtos {
    private EventTaskDtos() {}

    @Data
    public static class TaskWriteRequest {
        private String title;
        private String requirements;
        @JsonProperty("pReward")
        private Integer pReward;
        private Integer bountyReward;
        private String budgetNote;
        private Boolean anonymous;
    }

    @Data
    public static class AdminPublishRequest {
        private Integer claimFee;
        private Integer maxClaimants;
    }

    @Data
    public static class AdminReturnRequest {
        private String comment;
    }

    @Data
    public static class OfficialTaskRequest extends TaskWriteRequest {
        private Integer claimFee;
        private Integer maxClaimants;
    }

    @Data
    public static class CloseTaskRequest {
        private String reason;
    }

    @Data
    public static class ProofReviewRequest {
        private String comment;
    }

    @Data
    public static class CompletionRevokeRequest {
        private String reason;
    }

    @Data
    public static class AdminEditRequest extends TaskWriteRequest {
    }

    @Data
    public static class AdminPinRequest {
        private Boolean pinned;
    }

    @Data
    public static class AdminCancelClaimRequest {
        private String reason;
    }

    @Data
    public static class ImageVO {
        private Long id;
        private String label;
        private String url;
    }

    @Data
    public static class ProofVO {
        private Long id;
        private Long taskId;
        private Long claimId;
        private Long playerId;
        private String playerName;
        private Integer attemptNo;
        private String description;
        private String status;
        private String reviewComment;
        private LocalDateTime reviewedAt;
        private LocalDateTime createdAt;
        private List<ImageVO> images = Collections.emptyList();
    }

    @Data
    public static class ClaimVO {
        private Long id;
        private Long taskId;
        private Long playerId;
        private String playerName;
        private String taskTitle;
        private String taskRequirements;
        private String taskSeason;
        private String status;
        private Integer feeAmount;
        @JsonProperty("pReward")
        private Integer pReward;
        private Integer bountyReward;
        private LocalDateTime claimedAt;
        private LocalDateTime proofSubmittedAt;
        private LocalDateTime abandonedAt;
        private Boolean abandonRefunded;
        private LocalDateTime completedAt;
        private LocalDateTime terminatedAt;
        private LocalDateTime completionRevokedAt;
        private String completionRevokeReason;
        private String completionRevokedByName;
        private String adminCancelReason;
        private LocalDateTime reclaimAvailableAt;
        private Boolean freeAbandonAvailable;
        private LocalDateTime freeAbandonUntil;
        private List<ProofVO> proofs = Collections.emptyList();
    }

    @Data
    public static class TaskVO {
        private Long id;
        private String season;
        private Long publisherPlayerId;
        private String publisherName;
        private Boolean official;
        private Boolean pinned;
        private Boolean anonymous;
        private String title;
        private String requirements;
        @JsonProperty("pReward")
        private Integer pReward;
        private Integer bountyReward;
        private String budgetNote;
        private Integer anonymousFeeRateSnapshot;
        private Integer anonymousFeeAmount;
        private Integer claimFee;
        private Integer maxClaimants;
        private Integer claimedCount;
        private Integer completedCount;
        private Integer remainingSlots;
        private Integer escrowTotal;
        private Integer escrowRemaining;
        private String status;
        private String latestReviewComment;
        private LocalDateTime reviewedAt;
        private LocalDateTime publishedAt;
        private LocalDateTime closedAt;
        private String closeReason;
        private LocalDateTime createdAt;
        private Boolean ownedByViewer;
        private Boolean canClaim;
        private String viewerClaimStatus;
        private Long viewerClaimId;
        private LocalDateTime viewerReclaimAvailableAt;
        private List<ClaimVO> claims = Collections.emptyList();
    }

    @Data
    public static class TaskPublicSettingsVO {
        private Integer anonymousMinimumFee;
        private Integer anonymousFeeRate;
    }
}
