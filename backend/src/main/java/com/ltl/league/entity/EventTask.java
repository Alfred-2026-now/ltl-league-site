package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("event_tasks")
public class EventTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String season;
    private Long publisherPlayerId;
    private String publisherNameSnapshot;
    private Integer official;
    private Integer anonymous;
    private String title;
    private String requirements;
    private Integer pReward;
    private Integer bountyReward;
    private String budgetNote;
    private Integer anonymousFeeRateSnapshot;
    private Integer anonymousFeeAmount;
    private Integer claimFee;
    private Integer maxClaimants;
    private Integer claimedCount;
    private Integer completedCount;
    private Integer escrowTotal;
    private Integer escrowRemaining;
    private String status;
    private String latestReviewComment;
    private Long reviewedByPlayerId;
    private LocalDateTime reviewedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime closedAt;
    private String closeReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
