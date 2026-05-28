package com.ltl.league.admin.dto;

import lombok.Data;

import java.util.List;

@Data
public class MatchResultGameVO {
    private Long id;
    private Integer gameIndex;
    private String winner;
    private String blueTeam;
    private String redTeam;
    private Integer durationSeconds;
    private String sourceGameId;
    private String gameVersion;
    private List<MatchResultAttachmentVO> screenshots;
}
