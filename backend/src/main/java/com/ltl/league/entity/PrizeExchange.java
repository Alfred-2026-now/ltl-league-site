package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prize_exchanges")
public class PrizeExchange {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long prizeId;

    private Long playerId;

    private String playerName;

    private Integer costPoints;

    private String status;

    private String contactInfo;

    private String remark;

    private LocalDateTime processedAt;

    private String processedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
