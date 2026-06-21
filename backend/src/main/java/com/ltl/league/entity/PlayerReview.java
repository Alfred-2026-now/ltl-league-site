package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("player_reviews")
public class PlayerReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long playerId;

    private Long authorPlayerId;

    private String content;

    private String positions;

    private Integer anonymous;

    private Integer ratingCount;

    private Integer ratingSum;

    private Double confidenceScore;

    private Integer tipTotal;

    private Double popularityScore;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
