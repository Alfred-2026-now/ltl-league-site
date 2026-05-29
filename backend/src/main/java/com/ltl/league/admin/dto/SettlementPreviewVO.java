package com.ltl.league.admin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SettlementPreviewVO {
    private List<PLedgerPreviewVO> pLedgers = new ArrayList<>();
    private List<LuxuryTaxPreviewVO> luxuryTaxes = new ArrayList<>();
    private List<LoanFeePreviewVO> loanFees = new ArrayList<>();
    private List<ValuationPreviewVO> valuationChanges = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
