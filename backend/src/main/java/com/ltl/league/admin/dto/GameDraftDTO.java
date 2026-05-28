package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class GameDraftDTO {
    private Integer gameIndex;
    private String winner;
    private String blueTeam;
    private String redTeam;
    private Integer durationSeconds;
    private String sourceGameId;
    private String gameVersion;
}
