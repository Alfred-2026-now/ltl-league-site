package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("p_ledger")
public class PLedger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teamId;

    private Long matchId;

    private String type;

    private Integer amount;

    private String reason;

    private String version;

    private Integer isVoided;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
