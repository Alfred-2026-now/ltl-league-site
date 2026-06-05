package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class RuleParameterHistoryVO {
    private Long id;
    private String paramKey;
    private String paramName;
    private String groupKey;
    private String groupName;
    private String oldValue;
    private String newValue;
    private String operator;
    private String reason;
    private String createdAt;
}
