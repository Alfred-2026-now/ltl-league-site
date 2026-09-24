package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("event_task_reviews")
public class EventTaskReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String action;
    private Long operatorPlayerId;
    private String comment;
    private Integer claimFeeSnapshot;
    private Integer maxClaimantsSnapshot;
    private String beforeSnapshot;
    private String afterSnapshot;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
