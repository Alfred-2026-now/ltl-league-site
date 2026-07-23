package com.ltl.league.dto;

import lombok.Data;

@Data
public class CaptainPLedgerVO {
    private Long id;
    private Long matchId;
    private String type;
    private Integer amount;
    private String reason;
    private String version;
    private Integer balanceBefore;
    private Integer balanceAfter;
    private Integer isVoided;
    private String createdAt;
}
