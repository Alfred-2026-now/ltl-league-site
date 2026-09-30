package com.ltl.league.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.service.AdminAssetService;
import com.ltl.league.dto.PredictionDtos;
import com.ltl.league.entity.*;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PredictionService {

    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_SETTLED = "SETTLED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private static final int MIN_OPTIONS = 2;
    private static final int MAX_OPTIONS = 6;
    private static final int MAX_REWARD = 10_000_000;

    private final PredictionMapper predictionMapper;
    private final PredictionOptionMapper optionMapper;
    private final PredictionBetMapper betMapper;
    private final PlayerMapper playerMapper;
    private final PlayerDepositLedgerMapper depositLedgerMapper;
    private final PlayerBountyLedgerMapper bountyLedgerMapper;
    private final AdminAssetService adminAssetService;
    private final Clock clock;

    @Value("${ltl.league.current-season:s1}")
    private String currentSeason;

    public PredictionService(
            PredictionMapper predictionMapper,
            PredictionOptionMapper optionMapper,
            PredictionBetMapper betMapper,
            PlayerMapper playerMapper,
            PlayerDepositLedgerMapper depositLedgerMapper,
            PlayerBountyLedgerMapper bountyLedgerMapper,
            AdminAssetService adminAssetService,
            Clock clock) {
        this.predictionMapper = predictionMapper;
        this.optionMapper = optionMapper;
        this.betMapper = betMapper;
        this.playerMapper = playerMapper;
        this.depositLedgerMapper = depositLedgerMapper;
        this.bountyLedgerMapper = bountyLedgerMapper;
        this.adminAssetService = adminAssetService;
        this.clock = clock;
    }

    // ==================== 玩家端 ====================

    public List<PredictionDtos.PredictionVO> listPublic(Player viewer) {
        List<Prediction> predictions = predictionMapper.selectList(new LambdaQueryWrapper<Prediction>()
                .eq(Prediction::getSeason, currentSeason)
                .eq(Prediction::getDeleted, 0)
                .orderByDesc(Prediction::getCreatedAt));
        return predictions.stream().map(p -> toVO(p, viewer)).collect(Collectors.toList());
    }

    public PredictionDtos.PredictionVO getPublicDetail(Long predictionId, Player viewer) {
        return toVO(requirePrediction(predictionId), viewer);
    }

    /**
     * 投注或改票。每人每场一票，截止前可反复修改。
     */
    @Transactional
    public PredictionDtos.PredictionVO placeBet(Long playerId, Long predictionId, Long optionId) {
        Prediction prediction = predictionMapper.selectByIdForUpdate(predictionId);
        if (prediction == null) {
            throw new BusinessException(404, "竞猜不存在");
        }
        requireBettingOpen(prediction);
        PredictionOption option = requireOption(predictionId, optionId);

        PredictionBet existing = findBet(predictionId, playerId);
        if (existing == null) {
            PredictionBet bet = new PredictionBet();
            bet.setPredictionId(predictionId);
            bet.setOptionId(option.getId());
            bet.setPlayerId(playerId);
            betMapper.insert(bet);
        } else {
            existing.setOptionId(option.getId());
            betMapper.updateById(existing);
        }
        return toVO(prediction, playerMapper.selectById(playerId));
    }

    // ==================== 管理端 ====================

    public List<PredictionDtos.PredictionVO> listAdmin(String status) {
        LambdaQueryWrapper<Prediction> wrapper = new LambdaQueryWrapper<Prediction>()
                .eq(Prediction::getSeason, currentSeason)
                .eq(Prediction::getDeleted, 0);
        if (status != null && !status.isBlank()) {
            wrapper.eq(Prediction::getStatus, status);
        }
        wrapper.orderByDesc(Prediction::getCreatedAt);
        return predictionMapper.selectList(wrapper).stream().map(p -> toVO(p, null)).collect(Collectors.toList());
    }

    @Transactional
    public PredictionDtos.PredictionVO create(Long adminId, PredictionDtos.AdminCreateRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求不能为空");
        }
        String title = requireText(request.getTitle(), 200, "竞猜标题");
        String description = optionalText(request.getDescription(), 1000);
        List<String> labels = normalizeOptions(request.getOptions());
        int rewardP = requireReward(request.getRewardP(), "P币总奖励");
        int rewardBounty = requireReward(request.getRewardBounty(), "赏金总奖励");
        LocalDateTime deadlineAt = request.getDeadlineAt();
        if (deadlineAt == null || !deadlineAt.isAfter(now())) {
            throw new BusinessException(400, "截止时间必须晚于当前时间");
        }

        Prediction prediction = new Prediction();
        prediction.setSeason(currentSeason);
        prediction.setTitle(title);
        prediction.setDescription(description);
        prediction.setRewardPTotal(rewardP);
        prediction.setRewardBountyTotal(rewardBounty);
        prediction.setDeadlineAt(deadlineAt);
        prediction.setStatus(STATUS_PUBLISHED);
        prediction.setWinnerCount(0);
        prediction.setRewardPPerWinner(0);
        prediction.setRewardBountyPerWinner(0);
        prediction.setCreatedByPlayerId(adminId);
        predictionMapper.insert(prediction);
        int sort = 1;
        for (String label : labels) {
            PredictionOption option = new PredictionOption();
            option.setPredictionId(prediction.getId());
            option.setLabel(label);
            option.setSortOrder(sort++);
            optionMapper.insert(option);
        }
        return toVO(prediction, null);
    }

    /**
     * 有人投注后仅允许修改标题、说明和截止时间；选项与奖励锁定。
     * 截止时间可任意修改（提前到当前时间之前会立即停止投注）。
     */
    @Transactional
    public PredictionDtos.PredictionVO edit(Long adminId, Long predictionId, PredictionDtos.AdminEditRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求不能为空");
        }
        Prediction prediction = predictionMapper.selectByIdForUpdate(predictionId);
        if (prediction == null) {
            throw new BusinessException(404, "竞猜不存在");
        }
        if (!STATUS_PUBLISHED.equals(prediction.getStatus())) {
            throw new BusinessException(409, "竞猜已结束，不能修改");
        }
        prediction.setTitle(requireText(request.getTitle(), 200, "竞猜标题"));
        prediction.setDescription(optionalText(request.getDescription(), 1000));
        if (request.getDeadlineAt() != null) {
            prediction.setDeadlineAt(request.getDeadlineAt());
        }
        predictionMapper.updateById(prediction);
        return toVO(prediction, null);
    }

    public PredictionDtos.SettlePreviewVO settlePreview(Long predictionId, Long correctOptionId) {
        Prediction prediction = requireSettleable(predictionId);
        PredictionOption option = requireOption(predictionId, correctOptionId);
        List<PredictionBet> winnerBets = listBets(predictionId, option.getId());
        PredictionDtos.SettlePreviewVO vo = new PredictionDtos.SettlePreviewVO();
        vo.setPredictionId(prediction.getId());
        vo.setTitle(prediction.getTitle());
        vo.setCorrectOptionId(option.getId());
        vo.setCorrectOptionLabel(option.getLabel());
        vo.setWinnerCount(winnerBets.size());
        int n = winnerBets.size();
        if (n > 0) {
            vo.setRewardPPerWinner(halfUpDivide(prediction.getRewardPTotal(), n));
            vo.setRewardBountyPerWinner(halfUpDivide(prediction.getRewardBountyTotal(), n));
            vo.setActualPTotal(vo.getRewardPPerWinner() * n);
            vo.setActualBountyTotal(vo.getRewardBountyPerWinner() * n);
            vo.setWinners(winnerNames(winnerBets));
        } else {
            vo.setRewardPPerWinner(0);
            vo.setRewardBountyPerWinner(0);
            vo.setActualPTotal(0);
            vo.setActualBountyTotal(0);
        }
        return vo;
    }

    /**
     * 结算：两项总奖励分别平均分配给猜对选手，四舍五入到个位；
     * 联盟资产按实发金额记账，天然守恒；无人猜中不发放。
     */
    @Transactional
    public PredictionDtos.PredictionVO settle(Long adminId, Long predictionId, PredictionDtos.SettleRequest request) {
        Long correctOptionId = request == null ? null : request.getCorrectOptionId();
        Prediction prediction = predictionMapper.selectByIdForUpdate(predictionId);
        if (prediction == null) {
            throw new BusinessException(404, "竞猜不存在");
        }
        requireSettleableState(prediction);
        PredictionOption option = requireOption(predictionId, correctOptionId);

        List<PredictionBet> winnerBets = listBets(predictionId, option.getId());
        int n = winnerBets.size();
        int pShare = n > 0 ? halfUpDivide(prediction.getRewardPTotal(), n) : 0;        int bountyShare = n > 0 ? halfUpDivide(prediction.getRewardBountyTotal(), n) : 0;
        String adminName = playerName(adminId);
        String reasonPrefix = "竞猜中奖：" + prediction.getTitle();

        int actualPTotal = 0;
        int actualBountyTotal = 0;
        for (PredictionBet bet : winnerBets) {
            Player winner = playerMapper.selectByIdForUpdate(bet.getPlayerId());
            if (winner == null) {
                throw new BusinessException(404, "中奖选手不存在：" + bet.getPlayerId());
            }
            if (pShare > 0) {
                int before = safe(winner.getDeposit());
                winner.setDeposit(checkedAdd(before, pShare, "P币奖励"));
                addDepositLedger(winner, "prediction_reward", pShare, reasonPrefix, before, winner.getDeposit(),
                        "match_prediction", "match_prediction_bets", bet.getId(), adminName);
                actualPTotal += pShare;
            }
            if (bountyShare > 0) {
                int before = safe(winner.getBounty());
                winner.setBounty(checkedAdd(before, bountyShare, "赏金积分奖励"));
                PlayerBountyLedger bountyLedger = new PlayerBountyLedger();
                bountyLedger.setPlayerId(winner.getId());
                bountyLedger.setSeason(prediction.getSeason());
                bountyLedger.setType("prediction_reward");
                bountyLedger.setAmount(bountyShare);
                bountyLedger.setReason(reasonPrefix);
                bountyLedger.setBalanceBefore(before);
                bountyLedger.setBalanceAfter(winner.getBounty());
                bountyLedger.setOperator(adminName);
                bountyLedger.setRefTable("match_prediction_bets");
                bountyLedger.setRefId(bet.getId());
                bountyLedgerMapper.insert(bountyLedger);
                actualBountyTotal += bountyShare;
            }
            playerMapper.updateById(winner);
        }
        // 联盟资产按实际发放支出（赏金积分不占用联盟P币资产）
        adminAssetService.recordReversal(actualPTotal, "prediction_reward", "竞猜奖励发放：" + prediction.getTitle(),
                "match_prediction", "match_predictions", prediction.getId(), null, null, adminName);

        LocalDateTime settledAt = now();
        prediction.setCorrectOptionId(option.getId());
        prediction.setWinnerCount(n);
        prediction.setRewardPPerWinner(pShare);
        prediction.setRewardBountyPerWinner(bountyShare);
        prediction.setSettledAt(settledAt);
        prediction.setSettledByPlayerId(adminId);
        prediction.setStatus(STATUS_SETTLED);
        predictionMapper.updateById(prediction);
        return toVO(prediction, null);
    }

    @Transactional
    public PredictionDtos.PredictionVO cancel(Long adminId, Long predictionId, PredictionDtos.CancelRequest request) {
        String reason = requireText(request == null ? null : request.getReason(), 500, "作废原因");
        Prediction prediction = predictionMapper.selectByIdForUpdate(predictionId);
        if (prediction == null) {
            throw new BusinessException(404, "竞猜不存在");
        }
        if (!STATUS_PUBLISHED.equals(prediction.getStatus())) {
            throw new BusinessException(409, "竞猜已结束，不能作废");
        }
        prediction.setStatus(STATUS_CANCELLED);
        prediction.setCancelledAt(now());
        prediction.setCancelReason(reason);
        predictionMapper.updateById(prediction);
        return toVO(prediction, null);
    }

    /**
     * 撤回结算：按结算快照扣回每位中奖选手的P币与赏金，作废原奖励流水并记回退流水，
     * 联盟资产记回流；竞猜回到 PUBLISHED，可修改后重新结算。
     */
    @Transactional
    public PredictionDtos.PredictionVO revoke(Long adminId, Long predictionId, PredictionDtos.RevokeRequest request) {
        String reason = requireText(request == null ? null : request.getReason(), 500, "撤回原因");
        Prediction prediction = predictionMapper.selectByIdForUpdate(predictionId);
        if (prediction == null) {
            throw new BusinessException(404, "竞猜不存在");
        }
        if (!STATUS_SETTLED.equals(prediction.getStatus())) {
            throw new BusinessException(409, "只有已结算的竞猜可以撤回");
        }
        Long correctOptionId = prediction.getCorrectOptionId();
        if (correctOptionId == null) {
            throw new BusinessException(409, "竞猜结算记录异常，无法撤回");
        }
        int pShare = safe(prediction.getRewardPPerWinner());
        int bountyShare = safe(prediction.getRewardBountyPerWinner());
        String adminName = playerName(adminId);
        String reasonPrefix = "撤回竞猜结算：" + prediction.getTitle() + "；" + reason;

        int actualPTotal = 0;
        for (PredictionBet bet : listBets(predictionId, correctOptionId)) {
            Player winner = playerMapper.selectByIdForUpdate(bet.getPlayerId());
            if (winner == null) {
                throw new BusinessException(404, "中奖选手不存在：" + bet.getPlayerId());
            }
            if (pShare > 0) {
                // 作废原奖励流水，释放唯一键槽位，便于撤回后重新结算再发
                voidDepositRewardLedgers(winner.getId(), bet.getId(), reason);
                int before = safe(winner.getDeposit());
                winner.setDeposit(checkedAdd(before, -pShare, "P币奖励回退"));
                addDepositLedger(winner, "prediction_reward_reversal", -pShare, reasonPrefix, before, winner.getDeposit(),
                        "match_prediction", null, null, adminName);
                actualPTotal += pShare;
            }
            if (bountyShare > 0) {
                int before = safe(winner.getBounty());
                winner.setBounty(checkedAdd(before, -bountyShare, "赏金奖励回退"));
                PlayerBountyLedger bountyLedger = new PlayerBountyLedger();
                bountyLedger.setPlayerId(winner.getId());
                bountyLedger.setSeason(prediction.getSeason());
                bountyLedger.setType("prediction_reward_reversal");
                bountyLedger.setAmount(-bountyShare);
                bountyLedger.setReason(reasonPrefix);
                bountyLedger.setBalanceBefore(before);
                bountyLedger.setBalanceAfter(winner.getBounty());
                bountyLedger.setOperator(adminName);
                bountyLedger.setRefTable("match_prediction_bets");
                bountyLedger.setRefId(bet.getId());
                bountyLedgerMapper.insert(bountyLedger);
            }
            playerMapper.updateById(winner);
        }
        // 联盟资产记回流（金额与结算时的实发支出一致）
        if (actualPTotal > 0) {
            adminAssetService.recordIncome(actualPTotal, "prediction_reward_reversal",
                    "撤回竞猜奖励发放：" + prediction.getTitle(),
                    "match_prediction", "match_predictions", prediction.getId(), null, null, adminName);
        }

        prediction.setStatus(STATUS_PUBLISHED);
        prediction.setCorrectOptionId(null);
        prediction.setWinnerCount(0);
        prediction.setRewardPPerWinner(0);
        prediction.setRewardBountyPerWinner(0);
        prediction.setSettledAt(null);
        prediction.setSettledByPlayerId(null);
        // updateById 默认忽略 null 字段，用显式 SQL 清空结算快照
        predictionMapper.clearSettlement(prediction.getId());
        return toVO(prediction, null);
    }

    /**
     * 删除竞猜：仅移除展示（逻辑删除），不影响任何已发放的积分与流水。
     */
    @Transactional
    public void delete(Long adminId, Long predictionId) {
        Prediction prediction = predictionMapper.selectById(predictionId);
        if (prediction == null) {
            throw new BusinessException(404, "竞猜不存在");
        }
        predictionMapper.deleteById(predictionId);
    }

    private void voidDepositRewardLedgers(Long playerId, Long betId, String reason) {
        List<PlayerDepositLedger> rows = depositLedgerMapper.selectList(new LambdaQueryWrapper<PlayerDepositLedger>()
                .eq(PlayerDepositLedger::getPlayerId, playerId)
                .eq(PlayerDepositLedger::getType, "prediction_reward")
                .eq(PlayerDepositLedger::getRefTable, "match_prediction_bets")
                .eq(PlayerDepositLedger::getRefId, betId)
                .eq(PlayerDepositLedger::getIsVoided, 0));
        LocalDateTime voidedAt = now();
        for (PlayerDepositLedger row : rows) {
            row.setIsVoided(1);
            row.setVoidedAt(voidedAt);
            row.setVoidReason("撤回竞猜结算：" + reason);
            depositLedgerMapper.updateById(row);
        }
    }

    // ==================== 内部工具 ====================

    private void requireBettingOpen(Prediction prediction) {
        if (!STATUS_PUBLISHED.equals(prediction.getStatus())) {
            throw new BusinessException(409, "竞猜已结束，不能投注");
        }
        if (!now().isBefore(prediction.getDeadlineAt())) {
            throw new BusinessException(409, "已过截止时间，不能投注");
        }
    }

    private Prediction requireSettleable(Long predictionId) {
        Prediction prediction = predictionMapper.selectById(predictionId);
        if (prediction == null) {
            throw new BusinessException(404, "竞猜不存在");
        }
        requireSettleableState(prediction);
        return prediction;
    }

    private void requireSettleableState(Prediction prediction) {
        if (!STATUS_PUBLISHED.equals(prediction.getStatus())) {
            throw new BusinessException(409, "竞猜已结算或已作废");
        }
    }

    private Prediction requirePrediction(Long predictionId) {
        Prediction prediction = predictionMapper.selectById(predictionId);
        if (prediction == null) {
            throw new BusinessException(404, "竞猜不存在");
        }
        return prediction;
    }

    private PredictionOption requireOption(Long predictionId, Long optionId) {
        if (optionId == null) {
            throw new BusinessException(400, "请选择竞猜选项");
        }
        PredictionOption option = optionMapper.selectById(optionId);
        if (option == null || !Objects.equals(option.getPredictionId(), predictionId)) {
            throw new BusinessException(404, "竞猜选项不存在");
        }
        return option;
    }

    private PredictionBet findBet(Long predictionId, Long playerId) {
        return betMapper.selectList(new LambdaQueryWrapper<PredictionBet>()
                        .eq(PredictionBet::getPredictionId, predictionId)
                        .eq(PredictionBet::getPlayerId, playerId)
                        .eq(PredictionBet::getDeleted, 0))
                .stream().findFirst().orElse(null);
    }

    private List<PredictionBet> listBets(Long predictionId, Long optionId) {
        return betMapper.selectList(new LambdaQueryWrapper<PredictionBet>()
                .eq(PredictionBet::getPredictionId, predictionId)
                .eq(PredictionBet::getOptionId, optionId)
                .eq(PredictionBet::getDeleted, 0)
                .orderByAsc(PredictionBet::getId));
    }

    private List<PredictionDtos.WinnerVO> winnerNames(List<PredictionBet> bets) {
        if (bets.isEmpty()) {
            return Collections.emptyList();
        }
        List<Player> players = playerMapper.selectBatchIds(
                bets.stream().map(PredictionBet::getPlayerId).collect(Collectors.toList()));
        return players.stream()
                .map(player -> {
                    PredictionDtos.WinnerVO vo = new PredictionDtos.WinnerVO();
                    vo.setPlayerId(player.getId());
                    vo.setPlayerName(player.getName());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private PredictionDtos.PredictionVO toVO(Prediction prediction, Player viewer) {
        PredictionDtos.PredictionVO vo = new PredictionDtos.PredictionVO();
        vo.setId(prediction.getId());
        vo.setSeason(prediction.getSeason());
        vo.setTitle(prediction.getTitle());
        vo.setDescription(prediction.getDescription());
        vo.setRewardPTotal(safe(prediction.getRewardPTotal()));
        vo.setRewardBountyTotal(safe(prediction.getRewardBountyTotal()));
        vo.setDeadlineAt(prediction.getDeadlineAt());
        vo.setStatus(prediction.getStatus());
        vo.setBettingOpen(STATUS_PUBLISHED.equals(prediction.getStatus()) && now().isBefore(prediction.getDeadlineAt()));
        vo.setCorrectOptionId(prediction.getCorrectOptionId());
        vo.setWinnerCount(safe(prediction.getWinnerCount()));
        vo.setRewardPPerWinner(safe(prediction.getRewardPPerWinner()));
        vo.setRewardBountyPerWinner(safe(prediction.getRewardBountyPerWinner()));
        vo.setSettledAt(prediction.getSettledAt());
        vo.setCancelledAt(prediction.getCancelledAt());
        vo.setCancelReason(prediction.getCancelReason());
        vo.setCreatedAt(prediction.getCreatedAt());

        List<PredictionOption> options = optionMapper.selectList(new LambdaQueryWrapper<PredictionOption>()
                .eq(PredictionOption::getPredictionId, prediction.getId())
                .eq(PredictionOption::getDeleted, 0)
                .orderByAsc(PredictionOption::getSortOrder));
        List<PredictionBet> allBets = betMapper.selectList(new LambdaQueryWrapper<PredictionBet>()
                .eq(PredictionBet::getPredictionId, prediction.getId())
                .eq(PredictionBet::getDeleted, 0));
        vo.setTotalBets(allBets.size());
        Map<Long, Long> votesByOption = allBets.stream().collect(Collectors.groupingBy(
                PredictionBet::getOptionId, Collectors.counting()));
        boolean revealed = !STATUS_PUBLISHED.equals(prediction.getStatus());
        List<PredictionDtos.OptionVO> optionVOs = options.stream().map(option -> {
            PredictionDtos.OptionVO optionVO = new PredictionDtos.OptionVO();
            optionVO.setId(option.getId());
            optionVO.setLabel(option.getLabel());
            optionVO.setSortOrder(option.getSortOrder());
            optionVO.setIsCorrect(Objects.equals(option.getId(), prediction.getCorrectOptionId()));
            if (revealed) {
                long votes = votesByOption.getOrDefault(option.getId(), 0L);
                optionVO.setVoteCount((int) votes);
                optionVO.setVotePercent(allBets.isEmpty() ? 0 : (int) Math.round(votes * 100.0 / allBets.size()));
            }
            return optionVO;
        }).collect(Collectors.toList());
        vo.setOptions(optionVOs);
        optionVOs.stream()
                .filter(optionVO -> Objects.equals(optionVO.getId(), prediction.getCorrectOptionId()))
                .findFirst()
                .ifPresent(optionVO -> vo.setCorrectOptionLabel(optionVO.getLabel()));

        if (STATUS_SETTLED.equals(prediction.getStatus()) && safe(prediction.getWinnerCount()) > 0) {
            PredictionOption correct = options.stream()
                    .filter(option -> Objects.equals(option.getId(), prediction.getCorrectOptionId()))
                    .findFirst().orElse(null);
            if (correct != null) {
                vo.setWinners(winnerNames(listBets(prediction.getId(), correct.getId())));
            }
        }
        if (viewer != null) {
            PredictionBet viewerBet = allBets.stream()
                    .filter(bet -> Objects.equals(bet.getPlayerId(), viewer.getId()))
                    .findFirst().orElse(null);
            if (viewerBet != null) {
                vo.setViewerBetOptionId(viewerBet.getOptionId());
                options.stream()
                        .filter(option -> Objects.equals(option.getId(), viewerBet.getOptionId()))
                        .findFirst()
                        .ifPresent(option -> vo.setViewerBetOptionLabel(option.getLabel()));
                if (STATUS_SETTLED.equals(prediction.getStatus())) {
                    boolean won = Objects.equals(viewerBet.getOptionId(), prediction.getCorrectOptionId());
                    vo.setViewerWon(won);
                    if (won) {
                        vo.setViewerRewardP(safe(prediction.getRewardPPerWinner()));
                        vo.setViewerRewardBounty(safe(prediction.getRewardBountyPerWinner()));
                    }
                }
            }
        }
        return vo;
    }

    private void addDepositLedger(Player player, String type, int amount, String reason, int before, int after,
                                  String source, String refTable, Long refId, String operator) {
        PlayerDepositLedger ledger = new PlayerDepositLedger();
        ledger.setPlayerId(player.getId());
        ledger.setType(type);
        ledger.setAmount(amount);
        ledger.setReason(reason);
        ledger.setBalanceBefore(before);
        ledger.setBalanceAfter(after);
        ledger.setSource(source);
        ledger.setRefTable(refTable);
        ledger.setRefId(refId);
        ledger.setOperator(operator);
        ledger.setIsVoided(0);
        depositLedgerMapper.insert(ledger);
    }

    private List<String> normalizeOptions(List<String> options) {
        if (options == null) {
            throw new BusinessException(400, "请至少提供2个竞猜选项");
        }
        List<String> labels = options.stream()
                .map(label -> label == null ? "" : label.trim())
                .collect(Collectors.toList());
        if (labels.size() < MIN_OPTIONS || labels.size() > MAX_OPTIONS) {
            throw new BusinessException(400, "竞猜选项数量必须在2至6个之间");
        }
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 100) {
                throw new BusinessException(400, "选项内容不能为空且不超过100字");
            }
        }
        if (labels.stream().distinct().count() != labels.size()) {
            throw new BusinessException(400, "选项内容不能重复");
        }
        return labels;
    }

    private int requireReward(Integer reward, String field) {
        if (reward == null || reward < 1 || reward > MAX_REWARD) {
            throw new BusinessException(400, field + "必须在1至" + MAX_REWARD + "之间");
        }
        return reward;
    }

    private String requireText(String value, int maxLength, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(400, field + "不能为空");
        }
        if (value.trim().length() > maxLength) {
            throw new BusinessException(400, field + "不能超过" + maxLength + "字");
        }
        return value.trim();
    }

    private String optionalText(String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        if (value.trim().length() > maxLength) {
            throw new BusinessException(400, "说明不能超过" + maxLength + "字");
        }
        return value.trim();
    }

    /** 正整数四舍五入到个位的均分：HALF_UP。 */
    private int halfUpDivide(int total, int n) {
        return (total + n / 2) / n;
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private int checkedAdd(int a, int b, String field) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException e) {
            throw new BusinessException(409, field + "余额计算溢出");
        }
    }

    private String playerName(Long playerId) {
        Player player = playerId == null ? null : playerMapper.selectById(playerId);
        return player == null ? "admin" : player.getName();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
