package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class ManualPLedgerRequest {
    private Long teamId;
    private Integer amount;
    private String reason;
}
