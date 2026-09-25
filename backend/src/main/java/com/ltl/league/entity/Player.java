package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("players")
public class Player {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long teamId;

    private String name;

    private Integer value;

    private Integer topValue;

    private Integer jugValue;

    private Integer midValue;

    private Integer botValue;

    private Integer supValue;

    private Integer topActive;

    private Integer jugActive;

    private Integer midActive;

    private Integer botActive;

    private Integer supActive;

    private Integer maxValue;

    private String position;

    private String gameAccount;

    private String puuid;

    private Integer isSubstitute;

    private Integer isLoan;

    private Long loanTeamId;

    private Integer status;

    private Integer deposit;

    private Integer bounty;

    private Integer role;

    /** 下次身价衰减日期（未参赛衰减用）；为 null 表示尚未启动衰减计时 */
    private java.time.LocalDateTime nextDecayAt;

    /** 已发生的衰减次数（用于判断处于首阶段还是后续阶段） */
    private Integer decayCount;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
