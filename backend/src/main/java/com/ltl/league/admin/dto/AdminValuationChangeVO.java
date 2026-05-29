package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class AdminValuationChangeVO {
    private Long id;
    private Long matchId;
    private Long resultId;
    private Long playerId;
    private String playerName;
    private Long teamId;
    private String teamState;
    private Integer beforeValue;
    private Integer objectiveDelta;
    private Integer subjectiveDelta;
    private String subjectiveReason;
    private Integer afterValue;
    private String version;
    private String source;
    private Integer isVoided;
    private String createdAt;
}
