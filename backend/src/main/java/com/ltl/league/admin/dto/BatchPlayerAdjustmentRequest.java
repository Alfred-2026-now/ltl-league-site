package com.ltl.league.admin.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchPlayerAdjustmentRequest {
    private List<Long> playerIds;
    private Integer amount;
    private String reason;
}
