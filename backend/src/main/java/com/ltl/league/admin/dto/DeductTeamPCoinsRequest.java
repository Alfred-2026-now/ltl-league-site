package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class DeductTeamPCoinsRequest {
    private Long teamId;
    private Integer amount;
    private String reason;
}
