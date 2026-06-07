package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class AssetChangeVO {
    private String date;
    private Integer teamDelta;
    private Integer leagueDelta;
    private Integer playerDelta;
    private Integer totalDelta;
}
