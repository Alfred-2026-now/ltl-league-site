package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class ValuationInputDTO {
    private Long playerId;
    private Integer objectiveDelta;
    private Integer subjectiveDelta;
    private String subjectiveReason;
}
