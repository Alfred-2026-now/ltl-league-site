package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("match_predictions")
public class Prediction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String season;

    private Long matchId;

    private String title;

    private String description;

    private Integer rewardPTotal;

    private Integer rewardBountyTotal;

    private LocalDateTime deadlineAt;

    private String status;

    private Long correctOptionId;

    private Integer winnerCount;

    private Integer rewardPPerWinner;

    private Integer rewardBountyPerWinner;

    private LocalDateTime settledAt;

    private Long settledByPlayerId;

    private LocalDateTime cancelledAt;

    private String cancelReason;

    private Long createdByPlayerId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
