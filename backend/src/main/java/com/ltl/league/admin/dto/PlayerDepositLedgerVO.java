package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class PlayerDepositLedgerVO {
    private Long id;
    private Long playerId;
    private String playerName;
    private Long teamId;
    private String teamState;
    private Long matchId;
    private Long resultId;
    private String type;
    private Integer amount;
    private String reason;
    private Integer balanceBefore;
    private Integer balanceAfter;
    private String source;
    private String operator;
    private Integer isVoided;
    private String createdAt;
}
