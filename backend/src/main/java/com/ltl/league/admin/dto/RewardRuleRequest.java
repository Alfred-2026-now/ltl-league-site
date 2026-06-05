package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class RewardRuleRequest {
    private String format;
    private String scorePattern;
    private Integer winnerAmount;
    private Integer loserAmount;
    private Integer drawAmount;
    private Integer isActive;
    private String changeReason;
}
