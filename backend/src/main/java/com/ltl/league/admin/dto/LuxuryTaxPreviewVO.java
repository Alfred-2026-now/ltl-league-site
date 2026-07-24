package com.ltl.league.admin.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LuxuryTaxPreviewVO {
    private Long teamId;
    private String teamState;
    private BigDecimal lineValue;
    private Integer taxLine;
    private Integer taxable;
    private Integer tax;
}
