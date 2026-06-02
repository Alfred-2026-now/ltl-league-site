package com.ltl.league.dto;

import lombok.Data;

@Data
public class PlayerTransferRequest {
    private String recipientType;
    private Long recipientPlayerId;
    private Integer amount;
}
