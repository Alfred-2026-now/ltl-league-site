package com.ltl.league.admin.service;

import com.ltl.league.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class MatchSettlementCalculator {

    private static final double TAX_LINE_FACTOR = 0.92;
    private static final double[] BO3_TAX_RATES = {1, 1.3, 1.7, 2.2, 2.8};
    private static final double[] DEFAULT_TAX_RATES = {0.8, 1.1, 1.4, 1.8, 2.3};

    public LuxuryTaxResult calculateLuxuryTax(double leagueStandard, int lineValue, int rosterSize, String format) {
        double factor = getRosterFactor(rosterSize);
        double adjustedLineValue = lineValue * factor;
        double taxLine = leagueStandard * TAX_LINE_FACTOR;
        double taxable = Math.max(0, adjustedLineValue - taxLine);
        int tax = Math.toIntExact(Math.round(calculateProgressiveTax(taxable, format)));
        return new LuxuryTaxResult(factor, adjustedLineValue, taxLine, taxable, tax);
    }

    public LoanFeeResult calculateLoanFee(int playerValue, String format, String sourceType) {
        if ("BO5".equalsIgnoreCase(format)) {
            throw new BusinessException(400, "BO5 租借费比例未配置，无法发布");
        }
        double rate = "BO3".equalsIgnoreCase(format) ? 0.6 : 0.45;
        int fee = Math.toIntExact(Math.round(playerValue * rate));
        boolean freeAgent = "free_agent".equalsIgnoreCase(sourceType) || "free".equalsIgnoreCase(sourceType);
        int sourceTeamIncome = freeAgent ? 0 : Math.toIntExact(Math.round(fee * 0.4));
        int playerIncome = Math.toIntExact(Math.round(fee * 0.4));
        int leagueIncome = fee - sourceTeamIncome - playerIncome;
        return new LoanFeeResult(fee, playerIncome, sourceTeamIncome, leagueIncome);
    }

    public int calculateAfterValue(int beforeValue, int objectiveDelta, int subjectiveDelta) {
        int afterValue = beforeValue + objectiveDelta + subjectiveDelta;
        if (afterValue < 0) {
            throw new BusinessException(400, "身价调整后不能小于 0");
        }
        return afterValue;
    }

    public String scoreKey(int homeScore, int awayScore) {
        return homeScore + ":" + awayScore;
    }

    private double getRosterFactor(int rosterSize) {
        if (rosterSize <= 5) {
            return 1;
        }
        if (rosterSize == 6) {
            return 1.1;
        }
        if (rosterSize == 7) {
            return 1.25;
        }
        if (rosterSize == 8) {
            return 1.45;
        }
        if (rosterSize == 9) {
            return 1.7;
        }
        return 2;
    }

    private double calculateProgressiveTax(double taxable, String format) {
        double[] rates = "BO3".equalsIgnoreCase(format) ? BO3_TAX_RATES : DEFAULT_TAX_RATES;
        double[] parts = {
                Math.min(taxable, 1000),
                Math.max(Math.min(taxable - 1000, 1000), 0),
                Math.max(Math.min(taxable - 2000, 1000), 0),
                Math.max(Math.min(taxable - 3000, 1000), 0),
                Math.max(taxable - 4000, 0)
        };
        double total = 0;
        for (int i = 0; i < parts.length; i++) {
            total += parts[i] * rates[i];
        }
        return total;
    }

    public record LuxuryTaxResult(
            double factor,
            double adjustedLineValue,
            double taxLine,
            double taxable,
            int tax) {
    }

    public record LoanFeeResult(
            int fee,
            int playerIncome,
            int sourceTeamIncome,
            int leagueIncome) {
    }
}
