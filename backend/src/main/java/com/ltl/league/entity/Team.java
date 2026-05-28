package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("teams")
public class Team {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String state;

    private String name;

    private Integer pCoins;

    private Integer points;

    @TableField("`rank`")
    private Integer rank;

    private String logoUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
