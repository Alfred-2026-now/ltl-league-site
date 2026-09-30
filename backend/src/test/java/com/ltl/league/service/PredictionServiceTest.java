package com.ltl.league.service;

import com.ltl.league.admin.service.AdminAssetService;
import com.ltl.league.dto.PredictionDtos;
import com.ltl.league.entity.*;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictionServiceTest {
    @Mock private PredictionMapper predictionMapper;
    @Mock private PredictionOptionMapper optionMapper;
    @Mock private PredictionBetMapper betMapper;
    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerDepositLedgerMapper depositLedgerMapper;
    @Mock private PlayerBountyLedgerMapper bountyLedgerMapper;
    @Mock private AdminAssetService adminAssetService;

    /** 2026-10-01 12:00 北京时间 */
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private PredictionService service;

    @BeforeEach
    void setUp() {
        service = new PredictionService(predictionMapper, optionMapper, betMapper, playerMapper,
                depositLedgerMapper, bountyLedgerMapper, adminAssetService, clock);
        ReflectionTestUtils.setField(service, "currentSeason", "s2");
    }

    @Test
    void settleSplitsBothRewardsEvenlyWithHalfUpRounding() {
        Prediction prediction = prediction(10L, 100, 101, LocalDateTime.parse("2026-09-30T12:00:00"));
        PredictionOption win = option(21L, 10L, "选项A");
        List<PredictionBet> winnerBets = Arrays.asList(
                bet(51L, 10L, 21L, 1L), bet(52L, 10L, 21L, 2L), bet(53L, 10L, 21L, 3L));
        Player w1 = player(1L, "甲", 100, 10);
        Player w2 = player(2L, "乙", 200, 20);
        Player w3 = player(3L, "丙", 300, 30);
        Player admin = player(9L, "管理员", 0, 0);

        when(predictionMapper.selectByIdForUpdate(10L)).thenReturn(prediction);
        when(optionMapper.selectById(21L)).thenReturn(win);
        when(betMapper.selectList(any())).thenReturn(winnerBets);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(w1);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(w2);
        when(playerMapper.selectByIdForUpdate(3L)).thenReturn(w3);
        when(playerMapper.selectById(9L)).thenReturn(admin);
        when(playerMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(w1, w2, w3));
        when(optionMapper.selectList(any())).thenReturn(Collections.singletonList(win));

        PredictionDtos.SettleRequest request = new PredictionDtos.SettleRequest();
        request.setCorrectOptionId(21L);
        service.settle(9L, 10L, request);

        // 100P/3人 -> 33；101赏金/3人 -> (101+1)/3=34
        assertEquals(PredictionService.STATUS_SETTLED, prediction.getStatus());
        assertEquals(3, prediction.getWinnerCount());
        assertEquals(33, prediction.getRewardPPerWinner());
        assertEquals(34, prediction.getRewardBountyPerWinner());
        assertEquals(133, w1.getDeposit());
        assertEquals(44, w1.getBounty());
        assertEquals(233, w2.getDeposit());
        assertEquals(54, w2.getBounty());
        assertEquals(333, w3.getDeposit());
        assertEquals(64, w3.getBounty());

        // 流水：每人一条P币 + 一条赏金
        ArgumentCaptor<PlayerDepositLedger> depositLedger = ArgumentCaptor.forClass(PlayerDepositLedger.class);
        verify(depositLedgerMapper, times(3)).insert(depositLedger.capture());
        assertEquals("prediction_reward", depositLedger.getAllValues().get(0).getType());
        assertEquals(33, depositLedger.getAllValues().get(0).getAmount());
        assertEquals("match_prediction_bets", depositLedger.getAllValues().get(0).getRefTable());
        assertEquals(51L, depositLedger.getAllValues().get(0).getRefId());
        assertEquals("管理员", depositLedger.getAllValues().get(0).getOperator());

        ArgumentCaptor<PlayerBountyLedger> bountyLedger = ArgumentCaptor.forClass(PlayerBountyLedger.class);
        verify(bountyLedgerMapper, times(3)).insert(bountyLedger.capture());
        assertEquals(34, bountyLedger.getAllValues().get(0).getAmount());
        assertEquals("match_prediction_bets", bountyLedger.getAllValues().get(0).getRefTable());
        assertEquals(51L, bountyLedger.getAllValues().get(0).getRefId());

        // 联盟资产按实发支出：33*3=99
        verify(adminAssetService).recordReversal(eq(99), eq("prediction_reward"), any(), any(),
                eq("match_predictions"), eq(10L), isNull(), isNull(), eq("管理员"));
    }

    @Test
    void settleWithNoWinnersPaysNothing() {
        Prediction prediction = prediction(10L, 100, 100, LocalDateTime.parse("2026-09-30T12:00:00"));
        PredictionOption empty = option(21L, 10L, "无人选");
        PredictionOption other = option(22L, 10L, "有人选");
        when(predictionMapper.selectByIdForUpdate(10L)).thenReturn(prediction);
        when(optionMapper.selectById(22L)).thenReturn(other);
        when(betMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(playerMapper.selectById(9L)).thenReturn(player(9L, "管理员", 0, 0));
        when(optionMapper.selectList(any())).thenReturn(Arrays.asList(empty, other));

        PredictionDtos.SettleRequest request = new PredictionDtos.SettleRequest();
        request.setCorrectOptionId(22L);
        PredictionDtos.PredictionVO vo = service.settle(9L, 10L, request);

        assertEquals(PredictionService.STATUS_SETTLED, prediction.getStatus());
        assertEquals(0, prediction.getWinnerCount());
        assertEquals(0, prediction.getRewardPPerWinner());
        assertTrue(vo.getWinners().isEmpty());
        verify(depositLedgerMapper, never()).insert(any(PlayerDepositLedger.class));
        verify(bountyLedgerMapper, never()).insert(any(PlayerBountyLedger.class));
        verify(playerMapper, never()).updateById(any(Player.class));
    }

    @Test
    void settleAndRevokeWorkEvenBeforeDeadline() {
        // 截止时间在未来也允许直接结算；撤回后扣回奖励并回到进行中
        Prediction prediction = prediction(10L, 100, 100, LocalDateTime.parse("2026-10-02T12:00:00"));
        PredictionOption win = option(21L, 10L, "选项A");
        PredictionBet winnerBet = bet(51L, 10L, 21L, 1L);
        Player winner = player(1L, "甲", 50, 20);
        Player admin = player(9L, "管理员", 0, 0);
        PlayerDepositLedger rewardRow = new PlayerDepositLedger();
        rewardRow.setId(500L);
        rewardRow.setPlayerId(1L);
        rewardRow.setType("prediction_reward");
        rewardRow.setRefTable("match_prediction_bets");
        rewardRow.setRefId(51L);
        rewardRow.setIsVoided(0);

        when(predictionMapper.selectByIdForUpdate(10L)).thenReturn(prediction);
        when(optionMapper.selectById(21L)).thenReturn(win);
        when(betMapper.selectList(any())).thenReturn(Collections.singletonList(winnerBet));
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(winner);
        when(playerMapper.selectById(9L)).thenReturn(admin);
        when(playerMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(winner));
        when(optionMapper.selectList(any())).thenReturn(Collections.singletonList(win));
        when(depositLedgerMapper.selectList(any())).thenReturn(Collections.singletonList(rewardRow));

        PredictionDtos.SettleRequest settleRequest = new PredictionDtos.SettleRequest();
        settleRequest.setCorrectOptionId(21L);
        service.settle(9L, 10L, settleRequest);
        assertEquals(PredictionService.STATUS_SETTLED, prediction.getStatus());
        assertEquals(150, winner.getDeposit());
        assertEquals(120, winner.getBounty());

        PredictionDtos.RevokeRequest revokeRequest = new PredictionDtos.RevokeRequest();
        revokeRequest.setReason("选错了正确选项");
        PredictionDtos.PredictionVO vo = service.revoke(9L, 10L, revokeRequest);

        assertEquals(PredictionService.STATUS_PUBLISHED, prediction.getStatus());
        assertNull(prediction.getCorrectOptionId());
        assertEquals(0, prediction.getWinnerCount());
        assertEquals(50, winner.getDeposit());
        assertEquals(120 - 100, winner.getBounty());
        assertEquals(1, rewardRow.getIsVoided());
        assertNotNull(rewardRow.getVoidedAt());

        // 回退流水：负数金额、无业务引用（避免唯一键冲突）
        ArgumentCaptor<PlayerDepositLedger> depositLedger = ArgumentCaptor.forClass(PlayerDepositLedger.class);
        verify(depositLedgerMapper, times(2)).insert(depositLedger.capture());
        PlayerDepositLedger reversal = depositLedger.getAllValues().get(1);
        assertEquals("prediction_reward_reversal", reversal.getType());
        assertEquals(-100, reversal.getAmount());
        assertNull(reversal.getRefTable());
        assertNull(reversal.getRefId());

        ArgumentCaptor<PlayerBountyLedger> bountyLedger = ArgumentCaptor.forClass(PlayerBountyLedger.class);
        verify(bountyLedgerMapper, times(2)).insert(bountyLedger.capture());
        assertEquals(-100, bountyLedger.getAllValues().get(1).getAmount());

        // 联盟资产回流 100
        verify(adminAssetService).recordIncome(eq(100), eq("prediction_reward_reversal"), any(), any(),
                eq("match_predictions"), eq(10L), isNull(), isNull(), eq("管理员"));
        assertEquals(Boolean.TRUE, vo.getBettingOpen());
    }

    @Test
    void revokeRejectsNonSettledPrediction() {
        Prediction prediction = prediction(10L, 100, 100, LocalDateTime.parse("2026-09-30T12:00:00"));
        when(predictionMapper.selectByIdForUpdate(10L)).thenReturn(prediction);
        PredictionDtos.RevokeRequest request = new PredictionDtos.RevokeRequest();
        request.setReason("理由");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.revoke(9L, 10L, request));
        assertEquals(409, ex.getCode());
    }

    @Test
    void deleteOnlyHidesPredictionWithoutTouchingRewards() {
        Prediction prediction = prediction(10L, 100, 100, LocalDateTime.parse("2026-10-02T12:00:00"));
        when(predictionMapper.selectById(10L)).thenReturn(prediction);
        service.delete(9L, 10L);
        verify(predictionMapper).deleteById(10L);
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(depositLedgerMapper, never()).insert(any(PlayerDepositLedger.class));
    }

    @Test
    void placeBetInsertsFirstVoteThenUpdatesOnChange() {
        Prediction prediction = prediction(10L, 100, 100, LocalDateTime.parse("2026-10-02T12:00:00"));
        PredictionOption optionA = option(21L, 10L, "选项A");
        PredictionOption optionB = option(22L, 10L, "选项B");
        Player viewer = player(1L, "甲", 100, 10);
        when(predictionMapper.selectByIdForUpdate(10L)).thenReturn(prediction);
        when(optionMapper.selectById(21L)).thenReturn(optionA);
        when(optionMapper.selectById(22L)).thenReturn(optionB);
        when(betMapper.selectList(any()))
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.singletonList(bet(51L, 10L, 21L, 1L)));
        when(playerMapper.selectById(1L)).thenReturn(viewer);
        when(optionMapper.selectList(any())).thenReturn(Arrays.asList(optionA, optionB));

        PredictionDtos.BetRequest request = new PredictionDtos.BetRequest();
        request.setOptionId(21L);
        service.placeBet(1L, 10L, request.getOptionId());

        ArgumentCaptor<PredictionBet> inserted = ArgumentCaptor.forClass(PredictionBet.class);
        verify(betMapper).insert(inserted.capture());
        assertEquals(21L, inserted.getValue().getOptionId());
        assertEquals(1L, inserted.getValue().getPlayerId());

        request.setOptionId(22L);
        service.placeBet(1L, 10L, request.getOptionId());
        ArgumentCaptor<PredictionBet> updated = ArgumentCaptor.forClass(PredictionBet.class);
        verify(betMapper).updateById(updated.capture());
        assertEquals(22L, updated.getValue().getOptionId());
        verify(betMapper, times(1)).insert(any(PredictionBet.class));
    }

    @Test
    void placeBetRejectsAfterDeadlineOrClosed() {
        Prediction closed = prediction(10L, 100, 100, LocalDateTime.parse("2026-09-30T12:00:00"));
        when(predictionMapper.selectByIdForUpdate(10L)).thenReturn(closed);
        PredictionDtos.BetRequest request = new PredictionDtos.BetRequest();
        request.setOptionId(21L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.placeBet(1L, 10L, request.getOptionId()));
        assertEquals(409, ex.getCode());

        Prediction settled = prediction(11L, 100, 100, LocalDateTime.parse("2026-10-02T12:00:00"));
        settled.setStatus(PredictionService.STATUS_SETTLED);
        when(predictionMapper.selectByIdForUpdate(11L)).thenReturn(settled);
        assertThrows(BusinessException.class, () -> service.placeBet(1L, 11L, request.getOptionId()));
        verify(betMapper, never()).insert(any(PredictionBet.class));
    }

    @Test
    void placeBetRejectsForeignOption() {
        Prediction prediction = prediction(10L, 100, 100, LocalDateTime.parse("2026-10-02T12:00:00"));
        PredictionOption foreign = option(99L, 77L, "别的竞猜的选项");
        when(predictionMapper.selectByIdForUpdate(10L)).thenReturn(prediction);
        when(optionMapper.selectById(99L)).thenReturn(foreign);

        PredictionDtos.BetRequest request = new PredictionDtos.BetRequest();
        request.setOptionId(99L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.placeBet(1L, 10L, request.getOptionId()));
        assertEquals(404, ex.getCode());
    }

    @Test
    void createValidatesOptionsAndRewards() {
        PredictionDtos.AdminCreateRequest request = new PredictionDtos.AdminCreateRequest();
        request.setTitle("总决赛竞猜");
        request.setOptions(Arrays.asList("红队胜"));
        request.setRewardP(100);
        request.setRewardBounty(100);
        request.setDeadlineAt(LocalDateTime.parse("2026-10-02T12:00:00"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(9L, request));
        assertEquals(400, ex.getCode());

        request.setOptions(Arrays.asList("红队胜", "蓝队胜"));
        request.setRewardP(0);
        assertThrows(BusinessException.class, () -> service.create(9L, request));
        request.setRewardP(100);
        request.setRewardBounty(null);
        assertThrows(BusinessException.class, () -> service.create(9L, request));
        verify(predictionMapper, never()).insert(any(Prediction.class));
    }

    @Test
    void editAllowsAnyDeadlineChange() {
        Prediction prediction = prediction(10L, 100, 100, LocalDateTime.parse("2026-10-02T12:00:00"));
        when(predictionMapper.selectByIdForUpdate(10L)).thenReturn(prediction);
        when(optionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(betMapper.selectList(any())).thenReturn(Collections.emptyList());

        PredictionDtos.AdminEditRequest request = new PredictionDtos.AdminEditRequest();
        request.setTitle("新标题");
        request.setDeadlineAt(LocalDateTime.parse("2026-10-01T18:00:00")); // 早于原截止也允许
        service.edit(9L, 10L, request);
        assertEquals("新标题", prediction.getTitle());
        assertEquals(LocalDateTime.parse("2026-10-01T18:00:00"), prediction.getDeadlineAt());
    }

    @Test
    void cancelRequiresReasonAndOnlyPublished() {
        Prediction prediction = prediction(10L, 100, 100, LocalDateTime.parse("2026-10-02T12:00:00"));
        when(predictionMapper.selectByIdForUpdate(10L)).thenReturn(prediction);
        when(optionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(betMapper.selectList(any())).thenReturn(Collections.emptyList());

        PredictionDtos.CancelRequest request = new PredictionDtos.CancelRequest();
        request.setReason("比赛延期");
        service.cancel(9L, 10L, request);
        assertEquals(PredictionService.STATUS_CANCELLED, prediction.getStatus());
        assertEquals("比赛延期", prediction.getCancelReason());

        assertThrows(BusinessException.class, () -> service.cancel(9L, 10L, request));
        assertThrows(BusinessException.class,
                () -> service.cancel(9L, 10L, new PredictionDtos.CancelRequest()));
    }

    @Test
    void settlePreviewComputesSharesAndWinners() {
        Prediction prediction = prediction(10L, 100, 101, LocalDateTime.parse("2026-09-30T12:00:00"));
        PredictionOption win = option(21L, 10L, "选项A");
        List<PredictionBet> winnerBets = Arrays.asList(bet(51L, 10L, 21L, 1L), bet(52L, 10L, 21L, 2L));
        when(predictionMapper.selectById(10L)).thenReturn(prediction);
        when(optionMapper.selectById(21L)).thenReturn(win);
        when(betMapper.selectList(any())).thenReturn(winnerBets);
        when(playerMapper.selectBatchIds(anyCollection()))
                .thenReturn(Arrays.asList(player(1L, "甲", 0, 0), player(2L, "乙", 0, 0)));

        PredictionDtos.SettlePreviewVO vo = service.settlePreview(10L, 21L);
        assertEquals(2, vo.getWinnerCount());
        assertEquals(50, vo.getRewardPPerWinner());   // (100+1)/2
        assertEquals(51, vo.getRewardBountyPerWinner()); // (101+1)/2
        assertEquals(100, vo.getActualPTotal());
        assertEquals(102, vo.getActualBountyTotal());
        assertEquals(2, vo.getWinners().size());
    }

    // ==================== 构造工具 ====================

    private Prediction prediction(Long id, int rewardP, int rewardBounty, LocalDateTime deadlineAt) {
        Prediction prediction = new Prediction();
        prediction.setId(id);
        prediction.setSeason("s2");
        prediction.setTitle("总决赛竞猜");
        prediction.setRewardPTotal(rewardP);
        prediction.setRewardBountyTotal(rewardBounty);
        prediction.setDeadlineAt(deadlineAt);
        prediction.setStatus(PredictionService.STATUS_PUBLISHED);
        prediction.setWinnerCount(0);
        prediction.setRewardPPerWinner(0);
        prediction.setRewardBountyPerWinner(0);
        return prediction;
    }

    private PredictionOption option(Long id, Long predictionId, String label) {
        PredictionOption option = new PredictionOption();
        option.setId(id);
        option.setPredictionId(predictionId);
        option.setLabel(label);
        option.setSortOrder(1);
        return option;
    }

    private PredictionBet bet(Long id, Long predictionId, Long optionId, Long playerId) {
        PredictionBet bet = new PredictionBet();
        bet.setId(id);
        bet.setPredictionId(predictionId);
        bet.setOptionId(optionId);
        bet.setPlayerId(playerId);
        return bet;
    }

    private Player player(Long id, String name, int deposit, int bounty) {
        Player player = new Player();
        player.setId(id);
        player.setName(name);
        player.setDeposit(deposit);
        player.setBounty(bounty);
        return player;
    }
}
