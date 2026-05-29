package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("match_result_loan_inputs")
public class MatchResultLoanInput {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resultId;

    private Long matchId;

    private Long payingTeamId;

    private Long playerId;

    private Integer playerValue;

    private String sourceType;

    private Long sourceTeamId;

    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
