package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class RuleParameterVO {
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
    private String createdAt;
    private String updatedAt;
}
