package com.ltl.league.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlayerTransferVO {
    private Long id;
    private Long donorPlayerId;
    private String donorPlayerName;
    private String recipientType;
    private Long recipientPlayerId;
    private String recipientPlayerName;
    private Long recipientTeamId;
    private String recipientTeamName;
    private String recipientTeamState;
    private Integer amount;
    private Integer feeAmount;
    private Integer totalCost;
    private String status;
    private LocalDateTime createdAt;
}
