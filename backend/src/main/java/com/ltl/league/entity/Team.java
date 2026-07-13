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

    /** 赛季标识（如 s1/s2），与 matches.season 对齐，用于按赛季隔离战队 */
    private String season;

    private String name;

    private Integer pCoins;

    private Integer points;

    @TableField("`rank`")
    private Integer rank;

    private String logoUrl;

    /** 队伍简介 */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
