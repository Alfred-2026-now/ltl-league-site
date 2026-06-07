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
@TableName("league_asset_ledger")
public class LeagueAssetLedger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String type;

    private Integer amount;

    private String reason;

    private String source;

    private String refTable;

    private Long refId;

    private Long matchId;

    private Long resultId;

    private String operator;

    private Integer balanceBefore;

    private Integer balanceAfter;

    private Integer isVoided;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
