package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("player_deposit_ledger")
public class PlayerDepositLedger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long playerId;

    private Long matchId;

    private Long resultId;

    private String type;

    private Integer amount;

    private String reason;

    private Integer balanceBefore;

    private Integer balanceAfter;

    private String source;

    private String operator;

    private Integer isVoided;

    private LocalDateTime voidedAt;

    private String voidReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
