package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class PopulationSubsidyTeamVO {
    private Long teamId;
    private String teamState;
    private String teamName;
    private Integer eligiblePlayerCount;
    private Integer perPlayerAmount;
    private Integer subsidyAmount;
    private Integer balanceBefore;
    private Integer balanceAfter;
}
