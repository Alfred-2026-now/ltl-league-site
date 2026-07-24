package com.ltl.league.admin.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class LineupAppearanceInputDTO {
    private Long playerId;
    private String playerType;
    private Integer gamesPlayed;
    private BigDecimal playerValue;
    private List<Integer> advantageTiers = new ArrayList<>();
}
