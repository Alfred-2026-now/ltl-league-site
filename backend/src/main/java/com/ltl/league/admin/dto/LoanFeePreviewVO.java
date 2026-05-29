package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class LoanFeePreviewVO {
    private Long payingTeamId;
    private String payingTeamState;
    private Long sourceTeamId;
    private String sourceTeamState;
    private Long playerId;
    private String playerName;
    private Integer playerValue;
    private Integer fee;
    private Integer playerIncome;
    private Integer sourceTeamIncome;
    private Integer leagueIncome;
}
