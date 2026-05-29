package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class LoanFeeInputDTO {
    private Long payingTeamId;
    private Long playerId;
    private Integer playerValue;
    private String sourceType;
    private Long sourceTeamId;
    private String reason;
}
