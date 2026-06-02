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
@TableName("player_transfers")
public class PlayerTransfer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long donorPlayerId;

    private String recipientType;

    private Long recipientPlayerId;

    private Long recipientTeamId;

    private Integer amount;

    private Integer feeAmount;

    private Integer totalCost;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
