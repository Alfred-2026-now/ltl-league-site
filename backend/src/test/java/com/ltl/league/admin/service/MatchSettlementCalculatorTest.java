package com.ltl.league.admin.service;

import com.ltl.league.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchSettlementCalculatorTest {

    private final MatchSettlementCalculator calculator = new MatchSettlementCalculator();

    @Test
    void calculateLuxuryTaxUsesBo3ProgressiveRates() {
        MatchSettlementCalculator.LuxuryTaxResult result = calculator.calculateLuxuryTax(10000, 13000, 5, "BO3");

        assertEquals(1, result.factor());
        assertEquals(13000, result.adjustedLineValue());
        assertEquals(9200, result.taxLine());
        assertEquals(3800, result.taxable());
        assertEquals(5760, result.tax());
    }

    @Test
    void calculateLuxuryTaxUsesDefaultRatesForBo2() {
        MatchSettlementCalculator.LuxuryTaxResult result = calculator.calculateLuxuryTax(10000, 13000, 5, "BO2");

        assertEquals(4740, result.tax());
    }

    @Test
    void calculateLuxuryTaxAppliesRosterFactor() {
        MatchSettlementCalculator.LuxuryTaxResult result = calculator.calculateLuxuryTax(10000, 10000, 8, "BO2");

        assertEquals(1.45, result.factor());
        assertEquals(14500, result.adjustedLineValue());
        assertEquals(8090, result.tax());
    }

    @Test
    void calculateLuxuryTaxReturnsZeroWhenUnderTaxLine() {
        MatchSettlementCalculator.LuxuryTaxResult result = calculator.calculateLuxuryTax(10000, 8000, 5, "BO3");

        assertEquals(0, result.taxable());
        assertEquals(0, result.tax());
    }

    @Test
    void calculateLoanFeeUsesBo3RateForOriginalTeamLoan() {
        MatchSettlementCalculator.LoanFeeResult result = calculator.calculateLoanFee(3000, "BO3", "original_team");

        assertEquals(1800, result.fee());
        assertEquals(720, result.playerIncome());
        assertEquals(720, result.sourceTeamIncome());
        assertEquals(360, result.leagueIncome());
    }

    @Test
    void calculateLoanFeeUsesBo2RateForFreeAgentLoan() {
        MatchSettlementCalculator.LoanFeeResult result = calculator.calculateLoanFee(3000, "BO2", "free_agent");

        assertEquals(1350, result.fee());
        assertEquals(540, result.playerIncome());
        assertEquals(0, result.sourceTeamIncome());
        assertEquals(810, result.leagueIncome());
    }

    @Test
    void calculateLoanFeeBlocksBo5WhenRatioIsMissing() {
        assertThrows(BusinessException.class, () -> calculator.calculateLoanFee(3000, "BO5", "original_team"));
    }

    @Test
    void calculateAfterValueRejectsNegativeResult() {
        assertThrows(BusinessException.class, () -> calculator.calculateAfterValue(100, -80, -30));
    }

    @Test
    void scoreKeyUsesColonSeparatedScores() {
        assertEquals("1:1", calculator.scoreKey(1, 1));
    }
}
