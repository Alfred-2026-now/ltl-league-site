package com.ltl.league.admin.dto;

import lombok.Data;

import java.util.List;

@Data
public class PopulationSubsidyRequest {
    private List<Long> teamIds;
    private Integer perPlayerAmount;
    private String previewToken;
}
