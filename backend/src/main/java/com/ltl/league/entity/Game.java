package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("games")
public class Game {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long matchId;

    private Integer gameIndex;

    private String winner;

    private String blueTeam;

    private String redTeam;

    private String homeTeam;

    private String awayTeam;

    private Integer durationSeconds;

    private String sourceGameId;

    private String gameVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
