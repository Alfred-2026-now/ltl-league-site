package com.ltl.league.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlayerTransferPreviewVO {
    private String recipientType;
    private Long recipientPlayerId;
    private String recipientPlayerName;
    private Long recipientTeamId;
    private String recipientTeamName;
    private String recipientTeamState;
    private Integer amount;
    private Integer feeAmount;
    private Integer totalCost;
    private Integer donorBalance;
    private Integer balanceAfter;
    private Boolean allowed;
    private String message;
    private LocalDateTime nextPersonalTransferAt;
}
