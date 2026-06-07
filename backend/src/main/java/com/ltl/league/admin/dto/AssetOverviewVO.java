package com.ltl.league.admin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssetOverviewVO {
    private Integer teamAssets;
    private Integer leagueAssets;
    private Integer playerAssets;
    private Integer totalAssets;
    private List<AssetChangeVO> changes = new ArrayList<>();
}
