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

    private String position;

    private String gameAccount;

    private String puuid;

    private Integer isSubstitute;

    private Integer isLoan;

    private Long loanTeamId;

    private Integer status;

    private Integer deposit;

    private Integer role;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
