package com.ltl.league.admin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SettlementInputDTO {
    private Boolean taxExempt;
    private Integer homeLineValue;
    private Integer awayLineValue;
    private Integer homeRosterSize;
    private Integer awayRosterSize;
    private List<LoanFeeInputDTO> loanFees = new ArrayList<>();
    private List<ValuationInputDTO> valuationChanges = new ArrayList<>();
}
