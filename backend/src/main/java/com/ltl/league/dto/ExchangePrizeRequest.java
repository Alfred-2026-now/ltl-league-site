package com.ltl.league.dto;

import lombok.Data;

@Data
public class ExchangePrizeRequest {
    private Long prizeId;
    private String playerName;
    private String contactInfo;
    private String remark;
}
