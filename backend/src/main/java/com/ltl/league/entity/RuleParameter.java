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
@TableName("rule_parameters")
public class RuleParameter {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paramKey;

    private String groupKey;

    private String groupName;

    private String name;

    private String description;

    private String valueType;

    private String valueText;

    private String unit;

    private Integer sortOrder;

    private Integer isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
