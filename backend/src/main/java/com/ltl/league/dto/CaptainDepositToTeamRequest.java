package com.ltl.league.dto;

import lombok.Data;

/**
 * 队长个人账户向队伍账户转入资金请求（无税率）
 */
@Data
public class CaptainDepositToTeamRequest {

    /** 转入金额（P币） */
    private Integer amount;

    /** 备注（可选） */
    private String reason;
}
