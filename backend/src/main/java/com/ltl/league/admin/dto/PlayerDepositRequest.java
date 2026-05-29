package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class PlayerDepositRequest {
    private Long playerId;
    private Integer amount;
    private String reason;
}
