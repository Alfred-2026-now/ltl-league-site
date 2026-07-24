package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("match_result_lineup_inputs")
public class MatchResultLineupInput {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resultId;

    private Long matchId;

    private Long teamId;

    private Long playerId;

    private String playerType;

    private Integer gamesPlayed;

    private BigDecimal playerValue;

    private String advantageTiers;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
