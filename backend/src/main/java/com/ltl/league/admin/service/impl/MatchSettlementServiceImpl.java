package com.ltl.league.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ltl.league.admin.dto.LineupAppearanceInputDTO;
import com.ltl.league.admin.dto.LoanFeeInputDTO;
import com.ltl.league.admin.dto.LoanFeePreviewVO;
import com.ltl.league.admin.dto.LuxuryTaxPreviewVO;
import com.ltl.league.admin.dto.PLedgerPreviewVO;
import com.ltl.league.admin.dto.SettlementInputDTO;
import com.ltl.league.admin.dto.SettlementPreviewVO;
import com.ltl.league.admin.dto.ValuationInputDTO;
import com.ltl.league.admin.dto.ValuationPreviewVO;
import com.ltl.league.admin.service.AdminAssetService;
import com.ltl.league.admin.service.AdminPlayerDepositService;
import com.ltl.league.admin.service.MatchSettlementCalculator;
import com.ltl.league.admin.service.MatchSettlementService;
import com.ltl.league.admin.service.RuleParameterService;
import com.ltl.league.entity.Match;
import com.ltl.league.entity.MatchResult;
import com.ltl.league.entity.MatchResultLineupInput;
import com.ltl.league.entity.MatchResultLoanInput;
import com.ltl.league.entity.MatchResultValuationInput;
import com.ltl.league.entity.PLedger;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.SettlementRewardRule;
import com.ltl.league.entity.Team;
import com.ltl.league.entity.ValuationChange;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.MatchResultLoanInputMapper;
import com.ltl.league.mapper.MatchResultLineupInputMapper;
import com.ltl.league.mapper.MatchResultMapper;
import com.ltl.league.mapper.MatchMapper;
import com.ltl.league.mapper.MatchResultValuationInputMapper;
import com.ltl.league.mapper.PLedgerMapper;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.mapper.SettlementRewardRuleMapper;
import com.ltl.league.mapper.TeamMapper;
import com.ltl.league.mapper.ValuationChangeMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MatchSettlementServiceImpl implements MatchSettlementService {

    private final MatchSettlementCalculator calculator;
    private final MatchMapper matchMapper;
    private final MatchResultMapper matchResultMapper;
    private final MatchResultLineupInputMapper lineupInputMapper;
    private final MatchResultLoanInputMapper loanInputMapper;
    private final MatchResultValuationInputMapper valuationInputMapper;
    private final SettlementRewardRuleMapper rewardRuleMapper;
    private final PLedgerMapper pLedgerMapper;
    private final ValuationChangeMapper valuationChangeMapper;
    private final TeamMapper teamMapper;
    private final PlayerMapper playerMapper;
    private final AdminPlayerDepositService adminPlayerDepositService;
    private final RuleParameterService ruleParameterService;
    private final AdminAssetService adminAssetService;

    public MatchSettlementServiceImpl(
            MatchSettlementCalculator calculator,
            MatchMapper matchMapper,
            MatchResultMapper matchResultMapper,
            MatchResultLineupInputMapper lineupInputMapper,
            MatchResultLoanInputMapper loanInputMapper,
            MatchResultValuationInputMapper valuationInputMapper,
            SettlementRewardRuleMapper rewardRuleMapper,
            PLedgerMapper pLedgerMapper,
            ValuationChangeMapper valuationChangeMapper,
            TeamMapper teamMapper,
            PlayerMapper playerMapper,
            AdminPlayerDepositService adminPlayerDepositService,
            RuleParameterService ruleParameterService,
            AdminAssetService adminAssetService) {
        this.calculator = calculator;
        this.matchMapper = matchMapper;
        this.matchResultMapper = matchResultMapper;
        this.lineupInputMapper = lineupInputMapper;
        this.loanInputMapper = loanInputMapper;
        this.valuationInputMapper = valuationInputMapper;
        this.rewardRuleMapper = rewardRuleMapper;
        this.pLedgerMapper = pLedgerMapper;
        this.valuationChangeMapper = valuationChangeMapper;
        this.teamMapper = teamMapper;
        this.playerMapper = playerMapper;
        this.adminPlayerDepositService = adminPlayerDepositService;
        this.ruleParameterService = ruleParameterService;
        this.adminAssetService = adminAssetService;
    }

    @Override
    public SettlementPreviewVO preview(Match match, MatchResult result, SettlementInputDTO settlement) {
        SettlementPreviewVO preview = new SettlementPreviewVO();
        try {
            SettlementInputDTO input = normalizeSettlement(settlement);
            resolveLineValues(match, result, input, !Boolean.TRUE.equals(input.getTaxExempt()), result.getId() != null);
            List<LedgerDraft> ledgers = buildLedgerDrafts(match, result, input);
            applyBalancePreview(preview, ledgers);
            preview.setLuxuryTaxes(buildLuxuryTaxPreview(match, input));
            preview.setLoanFees(buildLoanFeePreview(match, input));
            preview.setValuationChanges(buildValuationPreview(input));
        } catch (BusinessException e) {
            preview.getErrors().add(e.getMessage());
        }
        return preview;
    }

    @Override
    public void apply(Match match, MatchResult result) {
        SettlementInputDTO settlement = loadInputs(match.getId(), result.getId());
        SettlementPreviewVO preview = preview(match, result, settlement);
        if (!preview.getErrors().isEmpty()) {
            throw new BusinessException(400, String.join("；", preview.getErrors()));
        }
        applyLedgers(match, result, buildLedgerDrafts(match, result, settlement));
        applyValuationChanges(match, result, settlement);
        applyTeamPoints(match, result);
    }

    @Override
    public void rollback(Match match, MatchResult result) {
        rollbackLedgers(match, result);
        rollbackValuations(match, result);
        rollbackTeamPoints(match, result);
    }

    @Override
    public void syncInputs(Match match, MatchResult result, SettlementInputDTO settlement) {
        SettlementInputDTO input = normalizeSettlement(settlement);
        resolveLineValues(match, result, input, false, false);
        result.setTaxExempt(Boolean.TRUE.equals(input.getTaxExempt()) ? 1 : 0);
        result.setHomeLineValue(input.getHomeLineValue());
        result.setAwayLineValue(input.getAwayLineValue());
        matchResultMapper.updateById(result);

        lineupInputMapper.delete(new LambdaQueryWrapper<MatchResultLineupInput>()
                .eq(MatchResultLineupInput::getResultId, result.getId()));
        persistLineupInputs(match, result, match.getHomeTeamId(), input.getHomeAppearances());
        persistLineupInputs(match, result, match.getAwayTeamId(), input.getAwayAppearances());

        loanInputMapper.delete(new LambdaQueryWrapper<MatchResultLoanInput>().eq(MatchResultLoanInput::getResultId, result.getId()));
        for (LoanFeeInputDTO dto : input.getLoanFees()) {
            if (dto.getPlayerId() == null) {
                continue;
            }
            MatchResultLoanInput row = new MatchResultLoanInput();
            row.setResultId(result.getId());
            row.setMatchId(match.getId());
            row.setPayingTeamId(dto.getPayingTeamId());
            row.setPlayerId(dto.getPlayerId());
            row.setReplacedPlayerId(dto.getReplacedPlayerId());
            row.setPlayerValue(dto.getPlayerValue());
            row.setSourceType(blankToDefault(dto.getSourceType(), "original_team"));
            row.setSourceTeamId(dto.getSourceTeamId());
            row.setReason(blankToNull(dto.getReason()));
            loanInputMapper.insert(row);
        }

        valuationInputMapper.delete(new LambdaQueryWrapper<MatchResultValuationInput>().eq(MatchResultValuationInput::getResultId, result.getId()));
        for (ValuationInputDTO dto : input.getValuationChanges()) {
            if (dto.getPlayerId() == null) {
                continue;
            }
            MatchResultValuationInput row = new MatchResultValuationInput();
            row.setResultId(result.getId());
            row.setMatchId(match.getId());
            row.setPlayerId(dto.getPlayerId());
            row.setObjectiveDelta(Optional.ofNullable(dto.getObjectiveDelta()).orElse(0));
            row.setSubjectiveDelta(Optional.ofNullable(dto.getSubjectiveDelta()).orElse(0));
            row.setSubjectiveReason(blankToNull(dto.getSubjectiveReason()));
            valuationInputMapper.insert(row);
        }
    }

    @Override
    public SettlementInputDTO loadInputs(Long matchId, Long resultId) {
        MatchResult result = matchResultMapper.selectById(resultId);
        SettlementInputDTO input = new SettlementInputDTO();
        if (result != null) {
            input.setTaxExempt(result.getTaxExempt() != null && result.getTaxExempt() == 1);
            input.setHomeLineValue(result.getHomeLineValue());
            input.setAwayLineValue(result.getAwayLineValue());
        }
        List<MatchResultLineupInput> lineupInputs = lineupInputMapper.selectList(
                new LambdaQueryWrapper<MatchResultLineupInput>()
                        .eq(MatchResultLineupInput::getResultId, resultId)
                        .eq(MatchResultLineupInput::getDeleted, 0)
                        .orderByAsc(MatchResultLineupInput::getId));
        Match match = matchMapper.selectById(matchId);
        if (match != null) {
            input.setHomeAppearances(toLineupDtos(lineupInputs, match.getHomeTeamId()));
            input.setAwayAppearances(toLineupDtos(lineupInputs, match.getAwayTeamId()));
        }
        List<MatchResultLoanInput> loanInputs = loanInputMapper.selectList(new LambdaQueryWrapper<MatchResultLoanInput>()
                .eq(MatchResultLoanInput::getResultId, resultId)
                .eq(MatchResultLoanInput::getDeleted, 0));
        input.setLoanFees(loanInputs.stream().map(row -> {
            LoanFeeInputDTO dto = new LoanFeeInputDTO();
            dto.setPayingTeamId(row.getPayingTeamId());
            dto.setPlayerId(row.getPlayerId());
            dto.setReplacedPlayerId(row.getReplacedPlayerId());
            dto.setPlayerValue(row.getPlayerValue());
            dto.setSourceType(row.getSourceType());
            dto.setSourceTeamId(row.getSourceTeamId());
            dto.setReason(row.getReason());
            return dto;
        }).collect(Collectors.toList()));

        List<MatchResultValuationInput> valuationInputs = valuationInputMapper.selectList(new LambdaQueryWrapper<MatchResultValuationInput>()
                .eq(MatchResultValuationInput::getResultId, resultId)
                .eq(MatchResultValuationInput::getDeleted, 0));
        input.setValuationChanges(valuationInputs.stream().map(row -> {
            ValuationInputDTO dto = new ValuationInputDTO();
            dto.setPlayerId(row.getPlayerId());
            dto.setObjectiveDelta(row.getObjectiveDelta());
            dto.setSubjectiveDelta(row.getSubjectiveDelta());
            dto.setSubjectiveReason(row.getSubjectiveReason());
            return dto;
        }).collect(Collectors.toList()));
        return input;
    }

    private void resolveLineValues(
            Match match,
            MatchResult result,
            SettlementInputDTO input,
            boolean strict,
            boolean preferStoredValues) {
        int totalGames = Optional.ofNullable(result.getHomeScore()).orElse(0)
                + Optional.ofNullable(result.getAwayScore()).orElse(0);
        int advantageGameLimit = maxGamesForFormat(match.getFormat());
        input.setHomeLineValue(resolveTeamLineValue(
                match.getHomeTeamId(),
                input.getHomeAppearances(),
                input.getLoanFees(),
                totalGames,
                advantageGameLimit,
                strict,
                preferStoredValues));
        input.setAwayLineValue(resolveTeamLineValue(
                match.getAwayTeamId(),
                input.getAwayAppearances(),
                input.getLoanFees(),
                totalGames,
                advantageGameLimit,
                strict,
                preferStoredValues));

        if (strict) {
            Set<Long> homePlayerIds = input.getHomeAppearances().stream()
                    .map(LineupAppearanceInputDTO::getPlayerId)
                    .collect(Collectors.toSet());
            Optional<Long> duplicated = input.getAwayAppearances().stream()
                    .map(LineupAppearanceInputDTO::getPlayerId)
                    .filter(homePlayerIds::contains)
                    .findFirst();
            if (duplicated.isPresent()) {
                Player player = requirePlayer(duplicated.get());
                throw new BusinessException(400, player.getName() + " 不能同时为主客两队出场");
            }
        }
    }

    private BigDecimal resolveTeamLineValue(
            Long teamId,
            List<LineupAppearanceInputDTO> appearances,
            List<LoanFeeInputDTO> loanFees,
            int totalGames,
            int advantageGameLimit,
            boolean strict,
            boolean preferStoredValues) {
        List<LineupAppearanceInputDTO> normalized = new ArrayList<>();
        Set<Long> playerIds = new HashSet<>();
        int appearanceSlots = 0;
        List<MatchSettlementCalculator.AppearanceValue> values = new ArrayList<>();

        for (LineupAppearanceInputDTO dto : appearances) {
            if (dto == null || dto.getPlayerId() == null || dto.getGamesPlayed() == null || dto.getGamesPlayed() <= 0) {
                if (strict) {
                    throw new BusinessException(400, "出场选手和出场局数不能为空");
                }
                continue;
            }
            if (!playerIds.add(dto.getPlayerId())) {
                Player duplicated = requirePlayer(dto.getPlayerId());
                throw new BusinessException(400, duplicated.getName() + " 在同一队伍的出场名单中重复");
            }
            if (totalGames > 0 && dto.getGamesPlayed() > totalGames) {
                Player player = requirePlayer(dto.getPlayerId());
                throw new BusinessException(400, player.getName() + " 的出场局数不能超过本场总局数 " + totalGames);
            }

            Player player = requirePlayer(dto.getPlayerId());
            boolean ownRosterPlayer = isOwnRosterPlayer(player, teamId);
            String playerType = "loan".equalsIgnoreCase(dto.getPlayerType()) ? "loan" : "roster";
            if ("roster".equals(playerType) && !ownRosterPlayer) {
                throw new BusinessException(400, player.getName() + " 不是该队当前可出场的本队选手");
            }
            if ("loan".equals(playerType) && ownRosterPlayer) {
                throw new BusinessException(400, player.getName() + " 是本队选手，不能登记为租借出场");
            }
            if (strict && "loan".equals(playerType) && !hasMatchingLoanFee(loanFees, teamId, player.getId())) {
                throw new BusinessException(400, player.getName() + " 已登记为租借出场，但租借费中没有对应记录");
            }

            int playerValue = preferStoredValues && dto.getPlayerValue() != null
                    ? dto.getPlayerValue().intValueExact()
                    : Optional.ofNullable(player.getValue()).orElse(0);
            List<Integer> advantageTiers = normalizeAdvantageTiers(dto.getAdvantageTiers(), advantageGameLimit);
            dto.setPlayerType(playerType);
            dto.setPlayerValue(BigDecimal.valueOf(playerValue));
            dto.setAdvantageTiers(advantageTiers);
            normalized.add(dto);
            appearanceSlots += dto.getGamesPlayed();
            values.add(new MatchSettlementCalculator.AppearanceValue(playerValue, dto.getGamesPlayed(), advantageTiers));
        }

        appearances.clear();
        appearances.addAll(normalized);
        if (totalGames <= 0 || normalized.isEmpty()) {
            if (strict) {
                throw new BusinessException(400, "请先填写有效比分，再选择出场选手和出场局数");
            }
            return null;
        }
        if (strict) {
            int requiredSlots = ruleParameterService.getInt("luxury.standard_roster_size") * totalGames;
            if (appearanceSlots != requiredSlots) {
                Team team = requireTeam(teamId);
                throw new BusinessException(400, team.getState() + " 出场局数合计应为 " + requiredSlots
                        + "（" + totalGames + " 局 × 每局 "
                        + ruleParameterService.getInt("luxury.standard_roster_size") + " 人），当前为 " + appearanceSlots);
            }
        }
        return calculator.calculateWeightedLineValue(values, totalGames, advantageGameLimit);
    }

    private boolean isOwnRosterPlayer(Player player, Long teamId) {
        return Objects.equals(player.getTeamId(), teamId)
                && (player.getStatus() == null || player.getStatus() == 1)
                && (player.getIsLoan() == null || player.getIsLoan() != 1);
    }

    private boolean hasMatchingLoanFee(List<LoanFeeInputDTO> loanFees, Long teamId, Long playerId) {
        return loanFees.stream().anyMatch(dto -> dto != null
                && Objects.equals(dto.getPayingTeamId(), teamId)
                && Objects.equals(dto.getPlayerId(), playerId));
    }

    private void persistLineupInputs(
            Match match,
            MatchResult result,
            Long teamId,
            List<LineupAppearanceInputDTO> appearances) {
        for (LineupAppearanceInputDTO dto : appearances) {
            MatchResultLineupInput row = new MatchResultLineupInput();
            row.setResultId(result.getId());
            row.setMatchId(match.getId());
            row.setTeamId(teamId);
            row.setPlayerId(dto.getPlayerId());
            row.setPlayerType(dto.getPlayerType());
            row.setGamesPlayed(dto.getGamesPlayed());
            row.setPlayerValue(dto.getPlayerValue());
            row.setAdvantageTiers(joinAdvantageTiers(dto.getAdvantageTiers()));
            lineupInputMapper.insert(row);
        }
    }

    private List<LineupAppearanceInputDTO> toLineupDtos(
            List<MatchResultLineupInput> rows,
            Long teamId) {
        return rows.stream()
                .filter(row -> Objects.equals(row.getTeamId(), teamId))
                .map(row -> {
                    LineupAppearanceInputDTO dto = new LineupAppearanceInputDTO();
                    dto.setPlayerId(row.getPlayerId());
                    dto.setPlayerType(row.getPlayerType());
                    dto.setGamesPlayed(row.getGamesPlayed());
                    dto.setPlayerValue(row.getPlayerValue());
                    dto.setAdvantageTiers(parseAdvantageTiers(row.getAdvantageTiers()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private List<Integer> normalizeAdvantageTiers(List<Integer> tiers, int advantageGameLimit) {
        List<Integer> normalized = tiers == null ? new ArrayList<>() : tiers.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Set<Integer> unique = new HashSet<>(normalized);
        if (unique.size() != normalized.size()
                || !Set.of(1000, 1500, 2000, 2500, 3000).containsAll(unique)) {
            throw new BusinessException(400, "身价差优势档位必须从 1000、1500、2000、2500、3000 中选择，且不能重复");
        }
        if (normalized.size() > advantageGameLimit) {
            throw new BusinessException(400, "每名选手勾选的身价差优势档位不能超过赛制总局数 " + advantageGameLimit);
        }
        return normalized;
    }

    private int maxGamesForFormat(String format) {
        if ("BO1".equalsIgnoreCase(format)) {
            return 1;
        }
        if ("BO2".equalsIgnoreCase(format)) {
            return 2;
        }
        if ("BO3".equalsIgnoreCase(format)) {
            return 3;
        }
        if ("BO5".equalsIgnoreCase(format)) {
            return 5;
        }
        throw new BusinessException(400, "不支持的比赛赛制: " + format);
    }

    private String joinAdvantageTiers(List<Integer> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return null;
        }
        return tiers.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Integer> parseAdvantageTiers(String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return java.util.Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(part -> !part.isEmpty())
                    .map(Integer::valueOf)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new BusinessException(500, "赛果中的身价差优势档位数据损坏");
        }
    }

    private List<LedgerDraft> buildLedgerDrafts(Match match, MatchResult result, SettlementInputDTO settlement) {
        List<LedgerDraft> ledgers = new ArrayList<>();
        ledgers.addAll(buildRewardLedgers(match, result));
        ledgers.addAll(buildLuxuryTaxLedgers(match, settlement));
        ledgers.addAll(buildLoanFeeLedgers(match, settlement));
        return ledgers;
    }

    private List<LedgerDraft> buildRewardLedgers(Match match, MatchResult result) {
        SettlementRewardRule rule = findRewardRule(match, result);
        Team home = requireTeam(match.getHomeTeamId());
        Team away = requireTeam(match.getAwayTeamId());
        List<LedgerDraft> ledgers = new ArrayList<>();
        String score = result.getHomeScore() + ":" + result.getAwayScore();
        if (Objects.equals(result.getHomeScore(), result.getAwayScore())) {
            if (rule.getDrawAmount() == null) {
                throw new BusinessException(400, match.getFormat() + " " + score + " 未配置平局奖励");
            }
            ledgers.add(new LedgerDraft(home.getId(), "match_reward", rule.getDrawAmount(), match.getFormat() + " " + score + " 平局奖励"));
            ledgers.add(new LedgerDraft(away.getId(), "match_reward", rule.getDrawAmount(), match.getFormat() + " " + score + " 平局奖励"));
            return ledgers;
        }
        Long winnerTeamId = result.getWinnerTeamId();
        if (winnerTeamId == null) {
            throw new BusinessException(400, "非平局赛果必须选择胜方");
        }
        Long loserTeamId = winnerTeamId.equals(home.getId()) ? away.getId() : home.getId();
        if (rule.getWinnerAmount() != null && rule.getWinnerAmount() != 0) {
            ledgers.add(new LedgerDraft(winnerTeamId, "match_reward", rule.getWinnerAmount(), match.getFormat() + " " + score + " 胜方奖励"));
        }
        if (rule.getLoserAmount() != null && rule.getLoserAmount() != 0) {
            ledgers.add(new LedgerDraft(loserTeamId, "match_reward", rule.getLoserAmount(), match.getFormat() + " " + score + " 败方奖励"));
        }
        return ledgers;
    }

    private SettlementRewardRule findRewardRule(Match match, MatchResult result) {
        String score = calculator.scoreKey(result.getHomeScore(), result.getAwayScore());
        SettlementRewardRule rule = rewardRuleMapper.selectOne(new LambdaQueryWrapper<SettlementRewardRule>()
                .eq(SettlementRewardRule::getFormat, match.getFormat())
                .eq(SettlementRewardRule::getScorePattern, score)
                .eq(SettlementRewardRule::getIsActive, 1)
                .eq(SettlementRewardRule::getDeleted, 0)
                .last("LIMIT 1"));
        if (rule == null) {
            throw new BusinessException(400, "未配置 " + match.getFormat() + " " + score + " 赛果奖励规则，无法发布");
        }
        return rule;
    }

    private List<LedgerDraft> buildLuxuryTaxLedgers(Match match, SettlementInputDTO settlement) {
        if (Boolean.TRUE.equals(settlement.getTaxExempt())) {
            return List.of();
        }
        requireTaxInput(settlement);
        double leagueStandard = calculateLeagueStandard();
        List<LedgerDraft> ledgers = new ArrayList<>();
        addLuxuryTaxLedger(ledgers, match.getHomeTeamId(), leagueStandard, settlement.getHomeLineValue(), match.getFormat());
        addLuxuryTaxLedger(ledgers, match.getAwayTeamId(), leagueStandard, settlement.getAwayLineValue(), match.getFormat());
        return ledgers;
    }

    private void requireTaxInput(SettlementInputDTO settlement) {
        if (settlement.getHomeLineValue() == null || settlement.getAwayLineValue() == null) {
            throw new BusinessException(400, "未标记免税时必须填写主客队出场选手及其出场局数");
        }
    }

    private List<LuxuryTaxPreviewVO> buildLuxuryTaxPreview(Match match, SettlementInputDTO settlement) {
        if (Boolean.TRUE.equals(settlement.getTaxExempt())) {
            return List.of();
        }
        requireTaxInput(settlement);
        double leagueStandard = calculateLeagueStandard();
        List<LuxuryTaxPreviewVO> rows = new ArrayList<>();
        rows.add(toLuxuryTaxPreview(match.getHomeTeamId(), leagueStandard, settlement.getHomeLineValue(), match.getFormat()));
        rows.add(toLuxuryTaxPreview(match.getAwayTeamId(), leagueStandard, settlement.getAwayLineValue(), match.getFormat()));
        return rows;
    }

    private LuxuryTaxPreviewVO toLuxuryTaxPreview(Long teamId, double leagueStandard, BigDecimal lineValue, String format) {
        Team team = requireTeam(teamId);
        MatchSettlementCalculator.LuxuryTaxResult result = calculator.calculateLuxuryTax(leagueStandard, lineValue.doubleValue(), format);
        LuxuryTaxPreviewVO vo = new LuxuryTaxPreviewVO();
        vo.setTeamId(teamId);
        vo.setTeamState(team.getState());
        vo.setLineValue(lineValue);
        vo.setTaxLine(Math.toIntExact(Math.round(result.taxLine())));
        vo.setTaxable(Math.toIntExact(Math.round(result.taxable())));
        vo.setTax(result.tax());
        return vo;
    }

    private void addLuxuryTaxLedger(List<LedgerDraft> ledgers, Long teamId, double leagueStandard, BigDecimal lineValue, String format) {
        MatchSettlementCalculator.LuxuryTaxResult result = calculator.calculateLuxuryTax(leagueStandard, lineValue.doubleValue(), format);
        if (result.tax() <= 0) {
            return;
        }
        String reason = "奢侈税：加权L=" + lineValue.stripTrailingZeros().toPlainString()
                + "，税线=" + Math.round(result.taxLine())
                + "，应税=" + Math.round(result.taxable());
        ledgers.add(new LedgerDraft(teamId, "luxury_tax", -result.tax(), reason));
    }

    private double calculateLeagueStandard() {
        List<Player> players = playerMapper.selectList(new LambdaQueryWrapper<Player>()
                .select(Player::getValue)
                .eq(Player::getStatus, 1)
                .eq(Player::getDeleted, 0));
        if (players.isEmpty()) {
            throw new BusinessException(400, "缺少在职选手，无法计算奢侈税税线");
        }
        double average = players.stream().map(Player::getValue).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0);
        return average * ruleParameterService.getInt("luxury.standard_roster_size");
    }

    private List<LedgerDraft> buildLoanFeeLedgers(Match match, SettlementInputDTO settlement) {
        List<LedgerDraft> ledgers = new ArrayList<>();
        for (LoanFeeInputDTO dto : settlement.getLoanFees()) {
            if (dto.getPlayerId() == null) {
                continue;
            }
            requireLoanInput(dto);
            Player player = requirePlayer(dto.getPlayerId());
            MatchSettlementCalculator.LoanFeeResult result = calculator.calculateLoanFee(dto.getPlayerValue(), match.getFormat(), dto.getSourceType());
            ledgers.add(new LedgerDraft(dto.getPayingTeamId(), "loan_fee", -result.fee(), loanReason(player, result, dto)));
            if (result.sourceTeamIncome() > 0) {
                ledgers.add(new LedgerDraft(dto.getSourceTeamId(), "loan_fee", result.sourceTeamIncome(), loanReason(player, result, dto)));
            }
        }
        return ledgers;
    }

    private void requireLoanInput(LoanFeeInputDTO dto) {
        if (dto.getPayingTeamId() == null || dto.getPlayerValue() == null || dto.getPlayerValue() < 0) {
            throw new BusinessException(400, "租借费输入缺少使用队伍或结算身价");
        }
        String sourceType = blankToDefault(dto.getSourceType(), "original_team");
        if ("original_team".equals(sourceType) && dto.getSourceTeamId() == null) {
            throw new BusinessException(400, "原队伍租借必须填写来源队伍");
        }
    }

    private String loanReason(Player player, MatchSettlementCalculator.LoanFeeResult result, LoanFeeInputDTO dto) {
        String extra = dto.getReason() == null || dto.getReason().isBlank() ? "" : "，" + dto.getReason().trim();
        return "租借 " + player.getName() + "，费用=" + result.fee() + "，选手=" + result.playerIncome()
                + "，原队=" + result.sourceTeamIncome() + "，联盟=" + result.leagueIncome() + extra;
    }

    private List<LoanFeePreviewVO> buildLoanFeePreview(Match match, SettlementInputDTO settlement) {
        List<LoanFeePreviewVO> rows = new ArrayList<>();
        Set<Long> teamIds = new HashSet<>();
        for (LoanFeeInputDTO dto : settlement.getLoanFees()) {
            if (dto.getPlayerId() == null) {
                continue;
            }
            teamIds.add(dto.getPayingTeamId());
            teamIds.add(dto.getSourceTeamId());
        }
        Map<Long, Team> teams = loadTeams(teamIds);
        for (LoanFeeInputDTO dto : settlement.getLoanFees()) {
            if (dto.getPlayerId() == null) {
                continue;
            }
            requireLoanInput(dto);
            Player player = requirePlayer(dto.getPlayerId());
            MatchSettlementCalculator.LoanFeeResult result = calculator.calculateLoanFee(dto.getPlayerValue(), match.getFormat(), dto.getSourceType());
            Team payingTeam = teams.get(dto.getPayingTeamId());
            Team sourceTeam = teams.get(dto.getSourceTeamId());
            LoanFeePreviewVO vo = new LoanFeePreviewVO();
            vo.setPayingTeamId(dto.getPayingTeamId());
            vo.setPayingTeamState(payingTeam != null ? payingTeam.getState() : "");
            vo.setSourceTeamId(dto.getSourceTeamId());
            vo.setSourceTeamState(sourceTeam != null ? sourceTeam.getState() : "");
            vo.setPlayerId(player.getId());
            vo.setPlayerName(player.getName());
            vo.setPlayerValue(dto.getPlayerValue());
            vo.setFee(result.fee());
            vo.setPlayerIncome(result.playerIncome());
            vo.setSourceTeamIncome(result.sourceTeamIncome());
            vo.setLeagueIncome(result.leagueIncome());
            rows.add(vo);
        }
        return rows;
    }

    private void applyBalancePreview(SettlementPreviewVO preview, List<LedgerDraft> ledgers) {
        Map<Long, Team> teams = loadTeams(ledgers.stream().map(LedgerDraft::teamId).collect(Collectors.toSet()));
        Map<Long, Integer> balances = teams.values().stream().collect(Collectors.toMap(Team::getId, Team::getPCoins));
        for (LedgerDraft draft : ledgers) {
            Team team = teams.get(draft.teamId());
            int before = balances.getOrDefault(draft.teamId(), 0);
            int after = before + draft.amount();
            balances.put(draft.teamId(), after);
            preview.getPLedgers().add(toLedgerPreview(draft, team, before, after));
        }
        // 在所有流水处理完后，检查最终余额为负的队伍，每个队伍只警告一次
        for (Map.Entry<Long, Integer> entry : balances.entrySet()) {
            if (entry.getValue() < 0) {
                Team team = teams.get(entry.getKey());
                preview.getWarnings().add(teamName(team) + " 结算后余额为 " + entry.getValue() + "P（不足）");
            }
        }
    }

    private void applyLedgers(Match match, MatchResult result, List<LedgerDraft> drafts) {
        Map<Long, Team> teams = loadTeams(drafts.stream().map(LedgerDraft::teamId).collect(Collectors.toSet()));
        Map<Long, Integer> balances = teams.values().stream().collect(Collectors.toMap(Team::getId, Team::getPCoins));
        String version = "r" + result.getVersionNo();

        List<MatchResultLoanInput> loanInputs = loanInputMapper.selectList(
                new LambdaQueryWrapper<MatchResultLoanInput>().eq(MatchResultLoanInput::getResultId, result.getId()));

        for (LedgerDraft draft : drafts) {
            Team team = teams.get(draft.teamId());
            int before = balances.getOrDefault(draft.teamId(), 0);
            int after = before + draft.amount();
            PLedger ledger = new PLedger();
            ledger.setTeamId(draft.teamId());
            ledger.setMatchId(match.getId());
            ledger.setResultId(result.getId());
            ledger.setType(draft.type());
            ledger.setAmount(draft.amount());
            ledger.setReason(draft.reason());
            ledger.setVersion(version);
            ledger.setSource("match_result");
            ledger.setBalanceBefore(before);
            ledger.setBalanceAfter(after);
            ledger.setIsVoided(0);
            pLedgerMapper.insert(ledger);
            if ("luxury_tax".equals(draft.type()) && draft.amount() < 0) {
                adminAssetService.recordIncome(
                        Math.abs(draft.amount()),
                        "luxury_tax",
                        draft.reason(),
                        "match_result",
                        "p_ledger",
                        ledger.getId(),
                        match.getId(),
                        result.getId(),
                        "system");
            }
            team.setPCoins(after);
            teamMapper.updateById(team);
            balances.put(team.getId(), after);
        }

        for (MatchResultLoanInput loanInput : loanInputs) {
            if (loanInput.getPlayerId() == null) {
                continue;
            }
            LoanFeeInputDTO dto = new LoanFeeInputDTO();
            dto.setPlayerId(loanInput.getPlayerId());
            dto.setPayingTeamId(loanInput.getPayingTeamId());
            dto.setSourceTeamId(loanInput.getSourceTeamId());
            dto.setPlayerValue(loanInput.getPlayerValue());
            dto.setSourceType(loanInput.getSourceType());

            Player player = playerMapper.selectById(dto.getPlayerId());
            if (player != null) {
                MatchSettlementCalculator.LoanFeeResult loanResult = calculator.calculateLoanFee(
                        dto.getPlayerValue(), match.getFormat(), dto.getSourceType());
                if (loanResult.playerIncome() > 0) {
                    adminPlayerDepositService.addLoanFeeToPlayer(player.getId(), loanResult.playerIncome());
                }
                if (loanResult.leagueIncome() > 0) {
                    adminAssetService.recordIncome(
                            loanResult.leagueIncome(),
                            "loan_fee",
                            loanReason(player, loanResult, dto),
                            "match_result",
                            "match_result_loan_inputs",
                            loanInput.getId(),
                            match.getId(),
                            result.getId(),
                            "system");
                }
            }
        }
    }

    private List<ValuationPreviewVO> buildValuationPreview(SettlementInputDTO settlement) {
        List<ValuationInputDTO> inputs = settlement.getValuationChanges();
        if (inputs.isEmpty()) {
            return List.of();
        }
        Map<Long, Player> players = loadPlayers(inputs.stream().map(ValuationInputDTO::getPlayerId).collect(Collectors.toSet()));
        List<ValuationPreviewVO> rows = new ArrayList<>();
        for (ValuationInputDTO input : inputs) {
            if (input.getPlayerId() == null) {
                continue;
            }
            Player player = players.get(input.getPlayerId());
            int objectiveDelta = Optional.ofNullable(input.getObjectiveDelta()).orElse(0);
            int subjectiveDelta = Optional.ofNullable(input.getSubjectiveDelta()).orElse(0);
            int afterValue = calculator.calculateAfterValue(player.getValue(), objectiveDelta, subjectiveDelta);
            ValuationPreviewVO row = new ValuationPreviewVO();
            row.setPlayerId(player.getId());
            row.setPlayerName(player.getName());
            row.setBeforeValue(player.getValue());
            row.setObjectiveDelta(objectiveDelta);
            row.setSubjectiveDelta(subjectiveDelta);
            row.setAfterValue(afterValue);
            row.setReason(input.getSubjectiveReason());
            rows.add(row);
        }
        return rows;
    }

    private void applyValuationChanges(Match match, MatchResult result, SettlementInputDTO settlement) {
        List<ValuationInputDTO> inputs = settlement.getValuationChanges();
        if (inputs.isEmpty()) {
            return;
        }
        Map<Long, Player> players = loadPlayers(inputs.stream().map(ValuationInputDTO::getPlayerId).collect(Collectors.toSet()));
        String version = "r" + result.getVersionNo();
        for (ValuationInputDTO input : inputs) {
            if (input.getPlayerId() == null) {
                continue;
            }
            Player player = players.get(input.getPlayerId());
            int objectiveDelta = Optional.ofNullable(input.getObjectiveDelta()).orElse(0);
            int subjectiveDelta = Optional.ofNullable(input.getSubjectiveDelta()).orElse(0);
            int afterValue = calculator.calculateAfterValue(player.getValue(), objectiveDelta, subjectiveDelta);
            ValuationChange change = new ValuationChange();
            change.setMatchId(match.getId());
            change.setResultId(result.getId());
            change.setPlayerId(player.getId());
            change.setBeforeValue(player.getValue());
            change.setObjectiveDelta(objectiveDelta);
            change.setSubjectiveDelta(subjectiveDelta);
            change.setSubjectiveReason(blankToNull(input.getSubjectiveReason()));
            change.setAfterValue(afterValue);
            change.setVersion(version);
            change.setSource("match_result");
            change.setOperator("admin");
            change.setIsVoided(0);
            valuationChangeMapper.insert(change);
            player.setValue(afterValue);
            playerMapper.updateById(player);
        }
    }

    private void rollbackLedgers(Match match, MatchResult result) {
        List<PLedger> ledgers = pLedgerMapper.selectList(new LambdaQueryWrapper<PLedger>()
                .eq(PLedger::getResultId, result.getId())
                .eq(PLedger::getIsVoided, 0)
                .eq(PLedger::getDeleted, 0)
                .orderByAsc(PLedger::getId));
        if (ledgers.isEmpty()) {
            ledgers = pLedgerMapper.selectList(new LambdaQueryWrapper<PLedger>()
                    .eq(PLedger::getMatchId, match.getId())
                    .eq(PLedger::getVersion, "r" + result.getVersionNo())
                    .eq(PLedger::getIsVoided, 0)
                    .eq(PLedger::getDeleted, 0)
                    .orderByAsc(PLedger::getId));
        }
        Map<Long, Integer> deltas = new HashMap<>();
        for (PLedger ledger : ledgers) {
            deltas.merge(ledger.getTeamId(), -ledger.getAmount(), Integer::sum);
        }
        Map<Long, Team> teams = loadTeams(deltas.keySet());
        for (Map.Entry<Long, Integer> entry : deltas.entrySet()) {
            Team team = teams.get(entry.getKey());
            int after = team.getPCoins() + entry.getValue();
            team.setPCoins(after);
            teamMapper.updateById(team);
        }
        if (!ledgers.isEmpty()) {
            pLedgerMapper.update(null, new LambdaUpdateWrapper<PLedger>()
                    .in(PLedger::getId, ledgers.stream().map(PLedger::getId).collect(Collectors.toList()))
                    .set(PLedger::getIsVoided, 1));
        }
        adminAssetService.reverseMatchResult(result.getId(), "赛果撤回，回滚联盟资产");
    }

    private void rollbackValuations(Match match, MatchResult result) {
        List<ValuationChange> changes = valuationChangeMapper.selectList(new LambdaQueryWrapper<ValuationChange>()
                .eq(ValuationChange::getResultId, result.getId())
                .eq(ValuationChange::getSource, "match_result")
                .eq(ValuationChange::getIsVoided, 0)
                .eq(ValuationChange::getDeleted, 0)
                .orderByDesc(ValuationChange::getId));
        if (changes.isEmpty()) {
            changes = valuationChangeMapper.selectList(new LambdaQueryWrapper<ValuationChange>()
                    .eq(ValuationChange::getMatchId, match.getId())
                    .eq(ValuationChange::getVersion, "r" + result.getVersionNo())
                    .eq(ValuationChange::getSource, "match_result")
                    .eq(ValuationChange::getIsVoided, 0)
                    .eq(ValuationChange::getDeleted, 0)
                    .orderByDesc(ValuationChange::getId));
        }
        Map<Long, Player> players = loadPlayers(changes.stream().map(ValuationChange::getPlayerId).collect(Collectors.toSet()));
        for (ValuationChange change : changes) {
            Player player = players.get(change.getPlayerId());
            if (!Objects.equals(player.getValue(), change.getAfterValue())) {
                throw new BusinessException(400, player.getName() + " 身价已被后续调整，请先处理后再撤回赛果");
            }
        }
        for (ValuationChange change : changes) {
            Player player = players.get(change.getPlayerId());
            player.setValue(change.getBeforeValue());
            playerMapper.updateById(player);
        }
        if (!changes.isEmpty()) {
            valuationChangeMapper.update(null, new LambdaUpdateWrapper<ValuationChange>()
                    .in(ValuationChange::getId, changes.stream().map(ValuationChange::getId).collect(Collectors.toList()))
                    .set(ValuationChange::getIsVoided, 1));
        }
    }

    private PLedgerPreviewVO toLedgerPreview(LedgerDraft draft, Team team, int before, int after) {
        PLedgerPreviewVO vo = new PLedgerPreviewVO();
        vo.setTeamId(draft.teamId());
        vo.setTeamState(team != null ? team.getState() : "");
        vo.setTeamName(team != null ? team.getName() : "");
        vo.setType(draft.type());
        vo.setAmount(draft.amount());
        vo.setReason(draft.reason());
        vo.setBalanceBefore(before);
        vo.setBalanceAfter(after);
        return vo;
    }

    private SettlementInputDTO normalizeSettlement(SettlementInputDTO settlement) {
        SettlementInputDTO input = settlement != null ? settlement : new SettlementInputDTO();
        if (input.getHomeAppearances() == null) {
            input.setHomeAppearances(new ArrayList<>());
        }
        if (input.getAwayAppearances() == null) {
            input.setAwayAppearances(new ArrayList<>());
        }
        if (input.getLoanFees() == null) {
            input.setLoanFees(new ArrayList<>());
        }
        if (input.getValuationChanges() == null) {
            input.setValuationChanges(new ArrayList<>());
        }
        return input;
    }

    private Team requireTeam(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException(400, "队伍不存在: " + teamId);
        }
        return team;
    }

    private Player requirePlayer(Long playerId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BusinessException(400, "选手不存在: " + playerId);
        }
        return player;
    }

    private Map<Long, Team> loadTeams(Set<Long> teamIds) {
        Set<Long> ids = teamIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return teamMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(Team::getId, Function.identity()));
    }

    private Map<Long, Player> loadPlayers(Set<Long> playerIds) {
        Set<Long> ids = playerIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Player> players = playerMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(Player::getId, Function.identity()));
        for (Long id : ids) {
            if (!players.containsKey(id)) {
                throw new BusinessException(400, "选手不存在: " + id);
            }
        }
        return players;
    }

    private String teamName(Team team) {
        return team != null ? team.getState() + " · " + team.getName() : "队伍";
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record LedgerDraft(Long teamId, String type, Integer amount, String reason) {
    }

    private void applyTeamPoints(Match match, MatchResult result) {
        if (result.getHomePoints() == null && result.getAwayPoints() == null) {
            return;
        }
        Team home = requireTeam(match.getHomeTeamId());
        Team away = requireTeam(match.getAwayTeamId());
        if (result.getHomePoints() != null && result.getHomePoints() != 0) {
            home.setPoints((home.getPoints() != null ? home.getPoints() : 0) + result.getHomePoints());
            teamMapper.updateById(home);
        }
        if (result.getAwayPoints() != null && result.getAwayPoints() != 0) {
            away.setPoints((away.getPoints() != null ? away.getPoints() : 0) + result.getAwayPoints());
            teamMapper.updateById(away);
        }
    }

    private void rollbackTeamPoints(Match match, MatchResult result) {
        if (result.getHomePoints() == null && result.getAwayPoints() == null) {
            return;
        }
        Team home = requireTeam(match.getHomeTeamId());
        Team away = requireTeam(match.getAwayTeamId());
        if (result.getHomePoints() != null && result.getHomePoints() != 0) {
            home.setPoints((home.getPoints() != null ? home.getPoints() : 0) - result.getHomePoints());
            teamMapper.updateById(home);
        }
        if (result.getAwayPoints() != null && result.getAwayPoints() != 0) {
            away.setPoints((away.getPoints() != null ? away.getPoints() : 0) - result.getAwayPoints());
            teamMapper.updateById(away);
        }
    }
}
