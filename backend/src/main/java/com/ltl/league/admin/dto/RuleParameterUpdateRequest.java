package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class RuleParameterUpdateRequest {
    private String valueText;
    private Integer isActive;
    private String reason;
}
