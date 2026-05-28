package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class AdminMatchListItemVO {
    private Long id;
    private String matchId;
    private String season;
    private Integer round;
    private String roundLabel;
    private String matchDate;
    private String format;
    private String status;

    private Long homeTeamId;
    private String homeTeamState;
    private Long awayTeamId;
    private String awayTeamState;

    private String liveUrl;
    private String notes;

    private Integer schedulePublished;

    private Boolean hasResultDraft;
    private Boolean hasPublishedResult;
}

