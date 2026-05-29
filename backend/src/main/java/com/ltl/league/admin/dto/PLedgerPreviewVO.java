package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class PLedgerPreviewVO {
    private Long teamId;
    private String teamState;
    private String teamName;
    private String type;
    private Integer amount;
    private String reason;
    private Integer balanceBefore;
    private Integer balanceAfter;
}
