package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("player_bounty_ledger")
public class PlayerBountyLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long playerId;
    private String season;
    private Long taskId;
    private Long claimId;
    private Long proofId;
    private String type;
    private Integer amount;
    private String reason;
    private Integer balanceBefore;
    private Integer balanceAfter;
    private String operator;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
