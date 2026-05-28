package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("matches")
public class Match {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String matchId;

    private String season;

    private Integer round;

    private String roundLabel;

    private LocalDateTime matchDate;

    private String format;

    private String status;

    private Long homeTeamId;

    private Long awayTeamId;

    private Integer homeScore;

    private Integer awayScore;

    private Integer homePoints;

    private Integer awayPoints;

    private Long forfeitTeamId;

    private String liveUrl;

    private String notes;

    private String source;

    private String version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
