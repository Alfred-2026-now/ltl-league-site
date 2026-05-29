package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class LuxuryTaxPreviewVO {
    private Long teamId;
    private String teamState;
    private Integer lineValue;
    private Integer rosterSize;
    private Double factor;
    private Integer adjustedLineValue;
    private Integer taxLine;
    private Integer taxable;
    private Integer tax;
}
