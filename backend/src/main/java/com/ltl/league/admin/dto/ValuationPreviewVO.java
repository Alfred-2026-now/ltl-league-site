package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class ValuationPreviewVO {
    private Long playerId;
    private String playerName;
    private Integer beforeValue;
    private Integer objectiveDelta;
    private Integer subjectiveDelta;
    private Integer afterValue;
    private String reason;
}
