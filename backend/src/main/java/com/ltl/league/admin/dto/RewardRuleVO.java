package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class RewardRuleVO {
    private Long id;
    private String format;
    private String scorePattern;
    private Integer winnerAmount;
    private Integer loserAmount;
    private Integer drawAmount;
    private Integer isActive;
    private String createdAt;
    private String updatedAt;
}
