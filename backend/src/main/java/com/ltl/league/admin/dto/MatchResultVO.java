package com.ltl.league.admin.dto;

import lombok.Data;

import java.util.List;

@Data
public class MatchResultVO {
    private Long id;
    private Long matchId;
    private Integer versionNo;
    private String status;
    private String resultType;
    private Integer homeScore;
    private Integer awayScore;
    private Long winnerTeamId;
    private String winnerTeamState;
    private Integer homePoints;
    private Integer awayPoints;
    private String notes;
    private String publishedAt;
    private String withdrawnAt;
    private String withdrawReason;
    private Boolean readOnly;
    private Boolean canCreateDraft;
    private List<MatchResultGameVO> games;
}
