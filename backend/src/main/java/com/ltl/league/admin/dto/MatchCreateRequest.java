package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class MatchCreateRequest {
    private Integer round;
    private String matchDate; // ISO-8601 string, nullable
    private String format;
    private Long homeTeamId;
    private Long awayTeamId;
    private String liveUrl;
    private String notes;
    private String roundLabel; // optional override
}

