package com.ltl.league.admin.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class SettlementInputDTO {
    private Boolean taxExempt;
    private BigDecimal homeLineValue;
    private BigDecimal awayLineValue;
    private List<LineupAppearanceInputDTO> homeAppearances = new ArrayList<>();
    private List<LineupAppearanceInputDTO> awayAppearances = new ArrayList<>();
    private List<LoanFeeInputDTO> loanFees = new ArrayList<>();
    private List<ValuationInputDTO> valuationChanges = new ArrayList<>();
}
