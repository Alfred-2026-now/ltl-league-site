package com.ltl.league.admin.dto;

import lombok.Data;

import java.util.List;

@Data
public class PopulationSubsidyResultVO {
    private List<PopulationSubsidyTeamVO> teams;
    private Integer selectedTeamCount;
    private Integer affectedTeamCount;
    private Integer eligiblePlayerCount;
    private Integer perPlayerAmount;
    private Integer totalAmount;
    private String previewToken;
    private String batchId;
    private Boolean applied;
}
