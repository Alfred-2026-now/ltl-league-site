package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("players")
public class Player {

    @TableId(type = IdType.AUTO)
    private Long id;

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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
