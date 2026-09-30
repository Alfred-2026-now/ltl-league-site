package com.ltl.league.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public final class PredictionDtos {
    private PredictionDtos() {}

    @Data
    public static class BetRequest {
        private Long optionId;
    }

    @Data
    public static class AdminCreateRequest {
        private String title;
        private String description;
        private Long matchId;
        private List<String> options;
        private Integer rewardP;
        private Integer rewardBounty;
        private LocalDateTime deadlineAt;
    }

    @Data
    public static class AdminEditRequest {
        private String title;
        private String description;
        private LocalDateTime deadlineAt;
    }

    @Data
    public static class SettleRequest {
        private Long correctOptionId;
    }

    @Data
    public static class CancelRequest {
        private String reason;
    }

    @Data
    public static class OptionVO {
        private Long id;
        private String label;
        private Integer sortOrder;
        /** 截止前不返回，结算/作废后公开 */
        private Integer voteCount;
        private Integer votePercent;
        private Boolean isCorrect;
    }

    @Data
    public static class WinnerVO {
        private Long playerId;
        private String playerName;
    }

    @Data
    public static class PredictionVO {
        private Long id;
        private String season;
        private Long matchId;
        private String matchRoundLabel;
        private LocalDateTime matchDate;
        private String matchFormat;
        private String homeTeamName;
        private String awayTeamName;
        private String title;
        private String description;
        private Integer rewardPTotal;
        private Integer rewardBountyTotal;
        private LocalDateTime deadlineAt;
        private String status;
        /** 状态为 PUBLISHED 且未过截止时间 */
        private Boolean bettingOpen;
        private Long correctOptionId;
        private String correctOptionLabel;
        private Integer winnerCount;
        private Integer rewardPPerWinner;
        private Integer rewardBountyPerWinner;
        private LocalDateTime settledAt;
        private LocalDateTime cancelledAt;
        private String cancelReason;
        private Integer totalBets;
        private List<OptionVO> options = Collections.emptyList();
        private List<WinnerVO> winners = Collections.emptyList();
        private Long viewerBetOptionId;
        private String viewerBetOptionLabel;
        private Boolean viewerWon;
        private Integer viewerRewardP;
        private Integer viewerRewardBounty;
        private LocalDateTime createdAt;
    }

    @Data
    public static class SettlePreviewVO {
        private Long predictionId;
        private String title;
        private Long correctOptionId;
        private String correctOptionLabel;
        private Integer winnerCount;
        private Integer rewardPPerWinner;
        private Integer rewardBountyPerWinner;
        private Integer actualPTotal;
        private Integer actualBountyTotal;
        private List<WinnerVO> winners = Collections.emptyList();
    }
}
