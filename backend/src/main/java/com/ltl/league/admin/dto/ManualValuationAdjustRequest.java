package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class ManualValuationAdjustRequest {
    private Long playerId;
    private Integer afterValue;
    private String reason;
}
