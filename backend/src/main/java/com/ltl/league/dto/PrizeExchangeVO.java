package com.ltl.league.dto;

import lombok.Data;

@Data
public class PrizeExchangeVO {
    private Long id;
    private Long prizeId;
    private String prizeName;
    private Long playerId;
    private String playerName;
    private Integer costPoints;
    private String status;
    private String contactInfo;
    private String remark;
    private String processedAt;
    private String processedBy;
    private String createdAt;
}
