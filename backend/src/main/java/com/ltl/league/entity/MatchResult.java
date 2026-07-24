package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("match_results")
public class MatchResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long matchId;

    private Integer versionNo;

    private String status;

    private String resultType;

    private Integer homeScore;

    private Integer awayScore;

    private Long winnerTeamId;

    private Integer homePoints;

    private Integer awayPoints;

    private String notes;

    private Integer taxExempt;

    private BigDecimal homeLineValue;

    private BigDecimal awayLineValue;

    private LocalDateTime publishedAt;

    private LocalDateTime withdrawnAt;

    private String withdrawReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
