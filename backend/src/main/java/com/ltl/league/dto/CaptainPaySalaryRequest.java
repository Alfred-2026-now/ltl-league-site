package com.ltl.league.dto;

import lombok.Data;

/**
 * 队长发工资请求
 */
@Data
public class CaptainPaySalaryRequest {

    /** 发放目标选手ID */
    private Long targetPlayerId;

    /** 发放金额（P币） */
    private Integer amount;

    /** 备注（可选） */
    private String reason;
}
