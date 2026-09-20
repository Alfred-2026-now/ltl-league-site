package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("event_task_proofs")
public class EventTaskProof {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long claimId;
    private Long playerId;
    private Integer attemptNo;
    private String description;
    private String status;
    private String reviewComment;
    private Long reviewedByPlayerId;
    private LocalDateTime reviewedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
