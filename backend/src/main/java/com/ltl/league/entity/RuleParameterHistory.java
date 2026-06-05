package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_parameter_histories")
public class RuleParameterHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parameterId;

    private String paramKey;

    private String paramName;

    private String groupKey;

    private String groupName;

    private String oldValue;

    private String newValue;

    private String operator;

    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
