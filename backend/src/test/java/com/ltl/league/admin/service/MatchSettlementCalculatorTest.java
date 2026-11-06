package com.ltl.league.admin.service;

import com.ltl.league.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchSettlementCalculatorTest {

    private final MatchSettlementCalculator calculator = new MatchSettlementCalculator();

    @Test
    void calculateLuxuryTaxUsesBo3ProgressiveRates() {
        MatchSettlementCalculator.LuxuryTaxResult result = calculator.calculateLuxuryTax(10000, 13000, "BO3");

        assertEquals(13000, result.lineValue());
        assertEquals(9200, result.taxLine());
        assertEquals(3800, result.taxable());
        assertEquals(5760, result.tax());
    }

    @Test
    void calculateLuxuryTaxUsesDefaultRatesForBo2() {
        MatchSettlementCalculator.LuxuryTaxResult result = calculator.calculateLuxuryTax(10000, 13000, "BO2");

        assertEquals(4740, result.tax());
    }

    @Test
    void calculateLuxuryTaxDoesNotApplyRosterFactor() {
        MatchSettlementCalculator.LuxuryTaxResult result = calculator.calculateLuxuryTax(10000, 10000, "BO2");

        assertEquals(10000, result.lineValue());
        assertEquals(640, result.tax());
    }

    @Test
    void calculateLuxuryTaxReturnsZeroWhenUnderTaxLine() {
        MatchSettlementCalculator.LuxuryTaxResult result = calculator.calculateLuxuryTax(10000, 8000, "BO3");

        assertEquals(0, result.taxable());
        assertEquals(0, result.tax());
    }

    @Test
    void calculateWeightedLineValueUsesEachPlayersShareOfGames() {
        BigDecimal result = calculator.calculateWeightedLineValue(List.of(
                new MatchSettlementCalculator.AppearanceValue(3000, 2, List.of()),
                new MatchSettlementCalculator.AppearanceValue(1500, 1, List.of())), 3, 3);

        assertEquals(new BigDecimal("2500.00"), result);
    }

    @Test
    void calculateWeightedLineValueRoundsOnlyAfterAccumulating() {
        BigDecimal result = calculator.calculateWeightedLineValue(List.of(
                new MatchSettlementCalculator.AppearanceValue(1000, 1, List.of())), 3, 3);

        assertEquals(new BigDecimal("333.33"), result);
    }

    @Test
    void calculateWeightedLineValueRejectsMoreAppearancesThanGames() {
        assertThrows(BusinessException.class, () -> calculator.calculateWeightedLineValue(List.of(
                new MatchSettlementCalculator.AppearanceValue(1000, 4, List.of())), 3, 3));
    }

    @Test
    void calculateWeightedLineValueAppliesAdvantageTiersBeforeAppearanceShare() {
        BigDecimal result = calculator.calculateWeightedLineValue(List.of(
                new MatchSettlementCalculator.AppearanceValue(4000, 2, List.of(1000, 1500))), 2, 2);

        assertEquals(new BigDecimal("2750.00"), result);
    }

    @Test
    void calculateWeightedLineValueLimitsAdvantageTierCountToTotalGames() {
        assertThrows(BusinessException.class, () -> calculator.calculateWeightedLineValue(List.of(
                new MatchSettlementCalculator.AppearanceValue(5000, 2, List.of(1000, 1500, 2000))), 2, 2));
    }

    @Test
    void calculateWeightedLineValueUsesFormatGameCountForAdvantageRule() {
        BigDecimal result = calculator.calculateWeightedLineValue(List.of(
                new MatchSettlementCalculator.AppearanceValue(4000, 2, List.of(1500))), 2, 3);

        assertEquals(new BigDecimal("3500.00"), result);
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
    void calculateAfterValueAllowsCrossingZeroAndRecovering() {
        assertEquals(-10, calculator.calculateAfterValue(100, -80, -30));
        assertEquals(-150, calculator.calculateAfterValue(-100, -80, 30));
        assertEquals(0, calculator.calculateAfterValue(-100, 80, 20));
        assertEquals(10, calculator.calculateAfterValue(-100, 80, 30));
        assertThrows(BusinessException.class, () -> calculator.calculateAfterValue(Integer.MIN_VALUE, -1, 0));
        assertThrows(BusinessException.class, () -> calculator.calculateAfterValue(Integer.MAX_VALUE, 1, 0));
    }

    @Test
    void negativeValuesReduceWeightedLineupIncludingAdvantageDiscount() {
        assertEquals(new BigDecimal("-250.00"), calculator.calculateWeightedLineValue(List.of(
                new MatchSettlementCalculator.AppearanceValue(-1000, 1, List.of()),
                new MatchSettlementCalculator.AppearanceValue(500, 1, List.of())), 2, 2));
        assertEquals(new BigDecimal("-500.00"), calculator.calculateWeightedLineValue(List.of(
                new MatchSettlementCalculator.AppearanceValue(500, 2, List.of(2000))), 2, 2));
        assertEquals(0, calculator.calculateLuxuryTax(-1000, -2000, "BO2").tax());
        assertEquals(336, calculator.calculateLuxuryTax(-1000, -500, "BO2").tax());
    }

    @Test
    void nonPositiveLoanValuesHaveNoFeesOrIncome() {
        for (int value : List.of(-1000, 0)) {
            for (String format : List.of("BO2", "BO3")) {
                for (String source : List.of("original_team", "free_agent")) {
                    assertEquals(new MatchSettlementCalculator.LoanFeeResult(0, 0, 0, 0),
                            calculator.calculateLoanFee(value, format, source));
                }
            }
        }
    }

    @Test
    void scoreKeyUsesColonSeparatedScores() {
        assertEquals("1:1", calculator.scoreKey(1, 1));
    }
}
