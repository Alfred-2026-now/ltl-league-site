package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class LeagueAssetLedgerVO {
    private Long id;
    private String type;
    private Integer amount;
    private String reason;
    private String source;
    private String refTable;
    private Long refId;
    private Long matchId;
    private Long resultId;
    private String operator;
    private Integer balanceBefore;
    private Integer balanceAfter;
    private Integer isVoided;
    private String createdAt;
}
