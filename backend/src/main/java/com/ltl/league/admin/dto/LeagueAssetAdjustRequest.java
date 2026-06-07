package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class LeagueAssetAdjustRequest {
    private Integer amount;
    private String reason;
}
