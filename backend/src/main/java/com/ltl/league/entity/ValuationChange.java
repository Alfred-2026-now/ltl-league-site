package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("valuation_changes")
public class ValuationChange {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long matchId;

    private Long playerId;

    private Integer beforeValue;

    private Integer objectiveDelta;

    private Integer subjectiveDelta;

    private String subjectiveReason;

    private Integer afterValue;

    private String version;

    private Integer isVoided;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
