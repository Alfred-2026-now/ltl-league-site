package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class RuleRequest {
    private String title;
    private String content;
    private Integer displayOrder;
    private Integer isOpen;
}
