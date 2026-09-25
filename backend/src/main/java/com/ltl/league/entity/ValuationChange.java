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

    private Long resultId;

    private Long playerId;

    private String position;

    private Integer beforeValue;

    private Integer objectiveDelta;

    private Integer subjectiveDelta;

    private String subjectiveReason;

    private Integer afterValue;

    private String version;

    private String source;

    private String operator;

    private Integer isVoided;

    /** 改动前的"下次衰减日期"，撤回时用于恢复衰减进程 */
    private LocalDateTime beforeNextDecayAt;

    /** 改动前的"已衰减次数"，撤回时用于恢复衰减进程 */
    private Integer beforeDecayCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
