package com.ltl.league.admin.service;

import com.ltl.league.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public LuxuryTaxResult calculateLuxuryTax(double leagueStandard, double lineValue, String format) {
        double taxLine = leagueStandard * ruleParameterService.getDecimal("luxury.tax_line_factor");
        double taxable = Math.max(ruleParameterService.getInt("luxury.taxable_floor"), lineValue - taxLine);
        int tax = Math.toIntExact(Math.round(calculateProgressiveTax(taxable, format)));
        return new LuxuryTaxResult(lineValue, taxLine, taxable, tax);
    }

    public BigDecimal calculateWeightedLineValue(
            List<AppearanceValue> appearances,
            int totalGames,
            int advantageGameLimit) {
        if (totalGames <= 0) {
            throw new BusinessException(400, "本场总局数必须大于 0");
        }
        if (advantageGameLimit <= 0) {
            throw new BusinessException(400, "赛制总局数必须大于 0");
        }
        BigDecimal weightedTotal = BigDecimal.ZERO;
        for (AppearanceValue appearance : appearances) {
            if (appearance.playerValue() < 0) {
                throw new BusinessException(400, "选手身价不能小于 0");
            }
            if (appearance.gamesPlayed() <= 0 || appearance.gamesPlayed() > totalGames) {
                throw new BusinessException(400, "选手出场局数必须在 1 到 " + totalGames + " 之间");
            }
            List<Integer> advantageTiers = appearance.advantageTiers() != null
                    ? appearance.advantageTiers()
                    : List.of();
            Set<Integer> uniqueTiers = new HashSet<>(advantageTiers);
            if (uniqueTiers.size() != advantageTiers.size()
                    || !Set.of(1000, 1500, 2000, 2500, 3000).containsAll(uniqueTiers)) {
                throw new BusinessException(400, "身价差优势档位必须从 1000、1500、2000、2500、3000 中选择，且不能重复");
            }
            if (advantageTiers.size() > advantageGameLimit) {
                throw new BusinessException(400, "身价差优势档位数量不能超过赛制总局数 " + advantageGameLimit);
            }
            int advantageTotal = advantageTiers.stream().mapToInt(Integer::intValue).sum();
            BigDecimal foldedPlayerValue = BigDecimal.valueOf(appearance.playerValue())
                    .subtract(BigDecimal.valueOf(advantageTotal)
                            .divide(BigDecimal.valueOf(advantageGameLimit), 10, RoundingMode.HALF_UP));
            if (foldedPlayerValue.signum() < 0) {
                throw new BusinessException(400, "身价差优势折算后选手身价不能小于 0");
            }
            weightedTotal = weightedTotal.add(
                    foldedPlayerValue
                            .multiply(BigDecimal.valueOf(appearance.gamesPlayed())));
        }
        return weightedTotal.divide(BigDecimal.valueOf(totalGames), 2, RoundingMode.HALF_UP);
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
            double lineValue,
            double taxLine,
            double taxable,
            int tax) {
    }

    public record AppearanceValue(int playerValue, int gamesPlayed, List<Integer> advantageTiers) {
    }

    public record LoanFeeResult(
            int fee,
            int playerIncome,
            int sourceTeamIncome,
            int leagueIncome) {
    }
}
