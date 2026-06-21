package com.ltl.league.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

public final class PlayerReviewDtos {

    private PlayerReviewDtos() {
    }

    @Data
    public static class CreateReviewRequest {
        private Long playerId;
        private List<String> positions = new ArrayList<>();
        private String content;
        private Boolean anonymous;
    }

    @Data
    public static class UpdateReviewRequest {
        private List<String> positions = new ArrayList<>();
        private String content;
        private Boolean anonymous;
    }

    @Data
    public static class RatingRequest {
        private Integer score;
    }

    @Data
    public static class TipRequest {
        private Integer amount;
    }

    @Data
    public static class ReviewVO {
        private Long id;
        private Long playerId;
        private String playerName;
        private String authorDisplayName;
        private Boolean anonymous;
        private List<String> positions = new ArrayList<>();
        private String content;
        private Integer ratingCount;
        private Integer ratingSum;
        private Double confidenceScore;
        private Integer tipTotal;
        private Double popularityScore;
        private Integer currentUserRating;
        private String createdAt;
        private Boolean canAdmin;
    }

    @Data
    public static class PlayerSummaryVO {
        private Long playerId;
        private Long teamId;
        private String playerName;
        private String teamState;
        private String teamName;
        private Integer value;
        private Integer deposit;
        private Integer reviewCount;
        private Double topPopularity;
        private Double totalPopularity;
        private String topReviewSnippet;
    }

    @Data
    public static class PlayerListResponse {
        private Long currentUserId;
        private Boolean admin;
        private List<PlayerSummaryVO> players = new ArrayList<>();
    }

    @Data
    public static class PlayerDetailResponse {
        private Long currentUserId;
        private Boolean admin;
        private PlayerSummaryVO player;
        private List<ReviewVO> reviews = new ArrayList<>();
    }
}
