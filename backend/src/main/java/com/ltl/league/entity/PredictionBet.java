package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("match_prediction_bets")
public class PredictionBet {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long predictionId;

    private Long optionId;

    private Long playerId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
