package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class AdminPLedgerVO {
    private Long id;
    private Long teamId;
    private String teamState;
    private String teamName;
    private Long matchId;
    private Long resultId;
    private String type;
    private Integer amount;
    private String reason;
    private String version;
    private String source;
    private Integer balanceBefore;
    private Integer balanceAfter;
    private Integer isVoided;
    private String createdAt;
}
