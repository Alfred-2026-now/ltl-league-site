package com.ltl.league.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrizeExchangeDetailVO {

    private Long id;
    private Long prizeId;
    private String prizeName;
    private Integer costPoints;
    private String status;
    private String statusText;
    private String contactInfo;
    private String remark;
    private LocalDateTime processedAt;
    private String processedBy;
    private LocalDateTime createdAt;
}
