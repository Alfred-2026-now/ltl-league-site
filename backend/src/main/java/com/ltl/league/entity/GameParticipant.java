package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("game_participants")
public class GameParticipant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long gameId;

    private Long playerId;

    private Long teamId;

    private Long sourceTeamId;

    private String position;

    private String champion;

    private Integer isLoan;

    private Integer isSubstitute;

    private Integer kills;

    private Integer deaths;

    private Integer assists;

    private Integer cs;

    private Integer goldEarned;

    private Integer damageDealt;

    private Integer damageTaken;

    private Integer visionScore;

    private BigDecimal killParticipation;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
