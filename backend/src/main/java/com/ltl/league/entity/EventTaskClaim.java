package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("event_task_claims")
public class EventTaskClaim {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long playerId;
    private String status;
    private Integer feeAmount;
    private Integer pRewardSnapshot;
    private Integer bountyRewardSnapshot;
    private String titleSnapshot;
    private String requirementsSnapshot;
    private LocalDateTime claimedAt;
    private LocalDateTime proofSubmittedAt;
    private LocalDateTime abandonedAt;
    private Integer abandonRefunded;
    private LocalDateTime completedAt;
    private LocalDateTime terminatedAt;
    private String adminCancelReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
