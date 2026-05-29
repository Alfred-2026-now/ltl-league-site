package com.ltl.league.admin.dto;

import lombok.Data;

import java.util.List;

@Data
public class MatchResultDraftRequest {
    private String resultType;
    private Integer homeScore;
    private Integer awayScore;
    private Long winnerTeamId;
    private Integer homePoints;
    private Integer awayPoints;
    private String notes;
    private SettlementInputDTO settlement;
    private List<GameDraftDTO> games;
}
