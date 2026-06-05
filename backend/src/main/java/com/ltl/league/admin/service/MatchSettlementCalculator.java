package com.ltl.league.admin.service;

import com.ltl.league.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MatchSettlementCalculator {

    private final RuleParameterService ruleParameterService;

    public MatchSettlementCalculator() {
        this(new CatalogRuleParameterService());
    }

    @Autowired
    public MatchSettlementCalculator(RuleParameterService ruleParameterService) {
        this.ruleParameterService = ruleParameterService;
    }

    public LuxuryTaxResult calculateLuxuryTax(double leagueStandard, int lineValue, int rosterSize, String format) {
        double factor = getRosterFactor(rosterSize);
        double adjustedLineValue = lineValue * factor;
        double taxLine = leagueStandard * ruleParameterService.getDecimal("luxury.tax_line_factor");
        double taxable = Math.max(ruleParameterService.getInt("luxury.taxable_floor"), adjustedLineValue - taxLine);
        int tax = Math.toIntExact(Math.round(calculateProgressiveTax(taxable, format)));
        return new LuxuryTaxResult(factor, adjustedLineValue, taxLine, taxable, tax);
    }

    public LoanFeeResult calculateLoanFee(int playerValue, String format, String sourceType) {
        if ("BO5".equalsIgnoreCase(format)) {
            throw new BusinessException(400, "BO5 租借费比例未配置，无法发布");
        }
        double rate = "BO3".equalsIgnoreCase(format)
                ? ruleParameterService.getDecimal("loan.bo3.rate")
                : ruleParameterService.getDecimal("loan.bo2.rate");
        int fee = Math.toIntExact(Math.round(playerValue * rate));
        boolean freeAgent = "free_agent".equalsIgnoreCase(sourceType) || "free".equalsIgnoreCase(sourceType);
        double sourceShare = freeAgent
                ? ruleParameterService.getDecimal("loan.free_agent_source_share")
                : ruleParameterService.getDecimal("loan.original_team_share");
        int sourceTeamIncome = Math.toIntExact(Math.round(fee * sourceShare));
        int playerIncome = Math.toIntExact(Math.round(fee * ruleParameterService.getDecimal("loan.player_share")));
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
            return ruleParameterService.getDecimal("luxury.roster_factor.le5");
        }
        if (rosterSize == 6) {
            return ruleParameterService.getDecimal("luxury.roster_factor.eq6");
        }
        if (rosterSize == 7) {
            return ruleParameterService.getDecimal("luxury.roster_factor.eq7");
        }
        if (rosterSize == 8) {
            return ruleParameterService.getDecimal("luxury.roster_factor.eq8");
        }
        if (rosterSize == 9) {
            return ruleParameterService.getDecimal("luxury.roster_factor.eq9");
        }
        return ruleParameterService.getDecimal("luxury.roster_factor.ge10");
    }

    private double calculateProgressiveTax(double taxable, String format) {
        String prefix = "BO3".equalsIgnoreCase(format) ? "luxury.bo3.rate.tier" : "luxury.bo2.rate.tier";
        double[] rates = {
                ruleParameterService.getDecimal(prefix + "1"),
                ruleParameterService.getDecimal(prefix + "2"),
                ruleParameterService.getDecimal(prefix + "3"),
                ruleParameterService.getDecimal(prefix + "4"),
                ruleParameterService.getDecimal(prefix + "5")
        };
        int tierWidth = ruleParameterService.getInt("luxury.tier_width");
        double[] parts = {
                Math.min(taxable, tierWidth),
                Math.max(Math.min(taxable - tierWidth, tierWidth), 0),
                Math.max(Math.min(taxable - tierWidth * 2, tierWidth), 0),
                Math.max(Math.min(taxable - tierWidth * 3, tierWidth), 0),
                Math.max(taxable - tierWidth * 4, 0)
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
