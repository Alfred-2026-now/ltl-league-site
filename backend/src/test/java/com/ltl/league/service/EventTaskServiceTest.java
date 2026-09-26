package com.ltl.league.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.service.AdminAssetService;
import com.ltl.league.admin.service.RuleParameterService;
import com.ltl.league.dto.EventTaskDtos;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventTaskServiceTest {
    @Mock private EventTaskMapper taskMapper;
    @Mock private EventTaskReviewMapper reviewMapper;
    @Mock private EventTaskClaimMapper claimMapper;
    @Mock private EventTaskProofMapper proofMapper;
    @Mock private EventTaskProofImageMapper proofImageMapper;
    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerDepositLedgerMapper depositLedgerMapper;
    @Mock private PlayerBountyLedgerMapper bountyLedgerMapper;
    @Mock private AdminAssetService adminAssetService;
    @Mock private RuleParameterService ruleParameterService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private EventTaskService service;

    @BeforeEach
    void setUp() {
        service = new EventTaskService(taskMapper, reviewMapper, claimMapper, proofMapper, proofImageMapper,
                playerMapper, depositLedgerMapper, bountyLedgerMapper, adminAssetService, ruleParameterService, clock);
        lenient().when(ruleParameterService.getInt("event_task.anonymous_fee_rate")).thenReturn(10);
        ReflectionTestUtils.setField(service, "currentSeason", "s2");
        ReflectionTestUtils.setField(service, "uploadDir", "build/test-task-uploads");
        ReflectionTestUtils.setField(service, "uploadUrlPrefix", "/uploads");
    }

    @Test
    void adminPublishFreezesWorstCaseRewardAndPublishesImmediately() {
        EventTask task = task(10L, 1L, 200, 30, EventTaskService.TASK_PENDING);
        Player publisher = player(1L, "发布者", 1500, 0);
        Player admin = player(9L, "管理员", 0, 0);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(publisher);
        when(playerMapper.selectById(9L)).thenReturn(admin);

        EventTaskDtos.AdminPublishRequest request = new EventTaskDtos.AdminPublishRequest();
        request.setClaimFee(20);
        request.setMaxClaimants(5);
        service.publishTask(9L, 10L, request);

        assertEquals(EventTaskService.TASK_PUBLISHED, task.getStatus());
        assertEquals(1000, task.getEscrowTotal());
        assertEquals(1000, task.getEscrowRemaining());
        assertEquals(500, publisher.getDeposit());

        ArgumentCaptor<PlayerDepositLedger> ledger = ArgumentCaptor.forClass(PlayerDepositLedger.class);
        verify(depositLedgerMapper).insert(ledger.capture());
        assertEquals("task_reward_escrow", ledger.getValue().getType());
        assertEquals(-1000, ledger.getValue().getAmount());
    }

    @Test
    void adminPublishRejectsInsufficientPublisherBalanceWithoutPublishing() {
        EventTask task = task(10L, 1L, 500, 0, EventTaskService.TASK_PENDING);
        Player publisher = player(1L, "发布者", 999, 0);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(publisher);
        EventTaskDtos.AdminPublishRequest request = new EventTaskDtos.AdminPublishRequest();
        request.setClaimFee(0);
        request.setMaxClaimants(2);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.publishTask(9L, 10L, request));

        assertTrue(error.getMessage().contains("需要冻结 1000P"));
        assertEquals(EventTaskService.TASK_PENDING, task.getStatus());
        verify(depositLedgerMapper, never()).insert(any());
    }

    @Test
    void anonymousPublishChargesHigherPercentageFeeAndStoresRateSnapshot() {
        EventTask task = task(10L, 1L, 201, 30, EventTaskService.TASK_PENDING);
        task.setAnonymous(1);
        Player publisher = player(1L, "发布者", 1106, 0);
        Player admin = player(9L, "管理员", 0, 0);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(publisher);
        when(playerMapper.selectById(9L)).thenReturn(admin);
        EventTaskDtos.AdminPublishRequest request = new EventTaskDtos.AdminPublishRequest();
        request.setClaimFee(20);
        request.setMaxClaimants(5);

        service.publishTask(9L, 10L, request);

        assertEquals(0, publisher.getDeposit());
        assertEquals(10, task.getAnonymousFeeRateSnapshot());
        assertEquals(101, task.getAnonymousFeeAmount());
        ArgumentCaptor<PlayerDepositLedger> ledger = ArgumentCaptor.forClass(PlayerDepositLedger.class);
        verify(depositLedgerMapper, times(2)).insert(ledger.capture());
        assertEquals(List.of("task_reward_escrow", "task_anonymous_fee"),
                ledger.getAllValues().stream().map(PlayerDepositLedger::getType).toList());
        assertEquals(-101, ledger.getAllValues().get(1).getAmount());
        verify(adminAssetService).recordIncome(eq(101), eq("task_anonymous_fee"), any(),
                eq("event_task"), eq("event_tasks"), eq(10L), isNull(), isNull(), eq("管理员"));
    }

    @Test
    void anonymousPublishUsesMinimumFeeAndRejectsCombinedInsufficientBalance() {
        EventTask task = task(10L, 1L, 100, 0, EventTaskService.TASK_PENDING);
        task.setAnonymous(1);
        Player publisher = player(1L, "发布者", 249, 0);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(publisher);
        EventTaskDtos.AdminPublishRequest request = new EventTaskDtos.AdminPublishRequest();
        request.setClaimFee(0);
        request.setMaxClaimants(2);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.publishTask(9L, 10L, request));

        assertTrue(error.getMessage().contains("匿名发布费 50P"));
        assertEquals(249, publisher.getDeposit());
        verify(depositLedgerMapper, never()).insert(any());
        verify(adminAssetService, never()).recordIncome(anyInt(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void officialTaskPublishesWithoutDebitingAdmin() {
        Player admin = player(9L, "管理员", 12, 0);
        when(playerMapper.selectById(9L)).thenReturn(admin);
        doAnswer(invocation -> {
            EventTask task = invocation.getArgument(0);
            task.setId(77L);
            return 1;
        }).when(taskMapper).insert(any(EventTask.class));
        EventTaskDtos.OfficialTaskRequest request = new EventTaskDtos.OfficialTaskRequest();
        request.setTitle("官方挑战");
        request.setRequirements("完成官方指定目标");
        request.setPReward(500);
        request.setBountyReward(20);
        request.setClaimFee(30);
        request.setMaxClaimants(10);

        EventTaskDtos.TaskVO result = service.publishOfficial(9L, request);

        assertTrue(result.getOfficial());
        assertEquals(EventTaskService.TASK_PUBLISHED, result.getStatus());
        assertEquals(12, admin.getDeposit());
        assertEquals(0, result.getEscrowTotal());
        verify(depositLedgerMapper, never()).insert(any());
    }

    @Test
    void publicTaskListDoesNotExposeAdminBudgetNote() {
        EventTask task = task(10L, 1L, 100, 20, EventTaskService.TASK_PUBLISHED);
        task.setBudgetNote("最多5人，供管理员审核");
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(task));

        List<EventTaskDtos.TaskVO> result = service.listPublic(null);

        assertEquals(1, result.size());
        assertNull(result.get(0).getBudgetNote());
        assertEquals(100, result.get(0).getPReward());
        assertEquals(20, result.get(0).getBountyReward());
    }

    @Test
    void adminCanPinAndUnpinPublishedTask() {
        EventTask task = task(10L, 1L, 100, 20, EventTaskService.TASK_PUBLISHED);
        Player admin = player(9L, "管理员", 0, 0);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        EventTaskDtos.AdminPinRequest request = new EventTaskDtos.AdminPinRequest();
        request.setPinned(true);

        assertTrue(service.setPinned(10L, request, admin).getPinned());
        assertEquals(1, task.getPinned());
        request.setPinned(false);
        assertFalse(service.setPinned(10L, request, admin).getPinned());
        assertEquals(0, task.getPinned());
        verify(taskMapper, times(2)).updateById(task);
    }

    @Test
    void cannotPinClosedTaskOrOmitValue() {
        EventTask task = task(10L, 1L, 100, 20, EventTaskService.TASK_PUBLISHED);
        Player admin = player(9L, "管理员", 0, 0);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        assertThrows(BusinessException.class,
                () -> service.setPinned(10L, new EventTaskDtos.AdminPinRequest(), admin));
        task.setStatus("CLOSED");
        EventTaskDtos.AdminPinRequest request = new EventTaskDtos.AdminPinRequest();
        request.setPinned(true);
        assertThrows(BusinessException.class, () -> service.setPinned(10L, request, admin));
        verify(taskMapper, never()).updateById(task);
    }

    @Test
    void anonymousTaskRedactsPublisherPubliclyButAdminStillSeesIdentity() {
        EventTask task = task(10L, 1L, 100, 20, EventTaskService.TASK_PUBLISHED);
        task.setAnonymous(1);
        task.setAnonymousFeeRateSnapshot(10);
        task.setAnonymousFeeAmount(50);
        Player admin = player(9L, "管理员", 0, 0);
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(task));

        EventTaskDtos.TaskVO publicTask = service.listPublic(null).get(0);
        EventTaskDtos.TaskVO adminTask = service.listAdminTasks(null, admin).get(0);

        assertEquals("匿名发布者", publicTask.getPublisherName());
        assertNull(publicTask.getPublisherPlayerId());
        assertNull(publicTask.getAnonymousFeeAmount());
        assertEquals("发布者", adminTask.getPublisherName());
        assertEquals(1L, adminTask.getPublisherPlayerId());
        assertTrue(adminTask.getAnonymous());
        assertEquals(50, adminTask.getAnonymousFeeAmount());
    }

    @Test
    void publisherCanStillSeeOwnBudgetNote() {
        EventTask task = task(10L, 1L, 100, 20, EventTaskService.TASK_PUBLISHED);
        task.setBudgetNote("最多5人，供管理员审核");
        Player publisher = player(1L, "发布者", 500, 0);
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(task));
        when(playerMapper.selectById(1L)).thenReturn(publisher);

        List<EventTaskDtos.TaskVO> result = service.listPublishedBy(1L);

        assertEquals("最多5人，供管理员审核", result.get(0).getBudgetNote());
    }

    @Test
    void claimDeductsFeeAndOccupiesOneSlot() {
        EventTask task = task(10L, 1L, 100, 20, EventTaskService.TASK_PUBLISHED);
        task.setClaimFee(50);
        task.setMaxClaimants(2);
        task.setClaimedCount(0);
        Player claimant = player(2L, "接取者", 80, 0);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(claimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(claimant);
        when(playerMapper.selectById(2L)).thenReturn(claimant);
        when(taskMapper.selectById(10L)).thenReturn(task);
        doAnswer(invocation -> {
            EventTaskClaim claim = invocation.getArgument(0);
            claim.setId(88L);
            return 1;
        }).when(claimMapper).insert(any(EventTaskClaim.class));

        EventTaskDtos.ClaimVO result = service.claim(2L, 10L);

        assertEquals(30, claimant.getDeposit());
        assertEquals(1, task.getClaimedCount());
        assertEquals(88L, result.getId());
        verify(adminAssetService).recordIncome(eq(50), eq("task_claim_fee"), any(),
                eq("event_task"), eq("event_task_claims"), eq(88L), isNull(), isNull(), eq("system"));
    }

    @Test
    void publisherCannotClaimOwnTask() {
        EventTask task = task(10L, 1L, 100, 20, EventTaskService.TASK_PUBLISHED);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);

        BusinessException error = assertThrows(BusinessException.class, () -> service.claim(1L, 10L));

        assertEquals("不能接取自己发布的任务", error.getMessage());
        verify(playerMapper, never()).selectByIdForUpdate(anyLong());
    }

    @Test
    void fullTaskRejectsAnotherClaimBeforeAnyDebit() {
        EventTask task = task(10L, 1L, 100, 20, EventTaskService.TASK_PUBLISHED);
        task.setClaimedCount(3);
        task.setMaxClaimants(3);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);

        BusinessException error = assertThrows(BusinessException.class, () -> service.claim(2L, 10L));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("名额已满"));
        verify(playerMapper, never()).selectByIdForUpdate(anyLong());
    }

    @Test
    void abandonAtExactlyThirtyMinutesRefundsFeeAndReturnsSlot() {
        EventTask task = task(10L, 1L, 100, 20, EventTaskService.TASK_PUBLISHED);
        task.setClaimedCount(1);
        EventTaskClaim claim = claim(88L, 10L, 2L, LocalDateTime.of(2026, 9, 20, 11, 30));
        Player claimant = player(2L, "接取者", 100, 0);
        when(claimMapper.selectById(88L)).thenReturn(claim);
        when(claimMapper.selectByIdForUpdate(88L)).thenReturn(claim);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(claimant);
        when(playerMapper.selectById(2L)).thenReturn(claimant);
        when(taskMapper.selectById(10L)).thenReturn(task);

        EventTaskDtos.ClaimVO result = service.abandonClaim(2L, 88L);

        assertEquals(EventTaskService.CLAIM_ABANDONED, claim.getStatus());
        assertTrue(result.getAbandonRefunded());
        assertEquals(150, claimant.getDeposit());
        assertEquals(0, task.getClaimedCount());
        verify(adminAssetService).recordReversal(eq(50), eq("task_claim_fee_refund"), any(),
                eq("event_task"), eq("event_task_claims"), eq(88L), isNull(), isNull(), eq("system"));
    }

    @Test
    void abandonAfterThirtyMinutesKeepsFeeButReturnsSlot() {
        EventTask task = task(10L, 1L, 100, 20, EventTaskService.TASK_PUBLISHED);
        task.setClaimedCount(1);
        EventTaskClaim claim = claim(88L, 10L, 2L, LocalDateTime.of(2026, 9, 20, 11, 29, 59));
        Player claimant = player(2L, "接取者", 100, 0);
        when(claimMapper.selectById(88L)).thenReturn(claim);
        when(claimMapper.selectByIdForUpdate(88L)).thenReturn(claim);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(playerMapper.selectById(2L)).thenReturn(claimant);
        when(taskMapper.selectById(10L)).thenReturn(task);

        EventTaskDtos.ClaimVO result = service.abandonClaim(2L, 88L);

        assertFalse(result.getAbandonRefunded());
        assertEquals(100, claimant.getDeposit());
        assertEquals(0, task.getClaimedCount());
        verify(adminAssetService, never()).recordReversal(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void approveProofPaysBothCurrenciesAndCannotReplay() {
        EventTask task = task(10L, 1L, 100, 25, EventTaskService.TASK_PUBLISHED);
        task.setEscrowRemaining(300);
        task.setCompletedCount(0);
        EventTaskClaim claim = claim(88L, 10L, 2L, LocalDateTime.of(2026, 9, 20, 11, 0));
        claim.setStatus(EventTaskService.PROOF_PENDING);
        EventTaskProof proof = new EventTaskProof();
        proof.setId(99L);
        proof.setTaskId(10L);
        proof.setClaimId(88L);
        proof.setPlayerId(2L);
        proof.setStatus(EventTaskService.PROOF_STATUS_PENDING);
        Player winner = player(2L, "完成者", 40, 7);
        Player admin = player(9L, "管理员", 0, 0);

        when(proofMapper.selectById(99L)).thenReturn(proof);
        when(proofMapper.selectByIdForUpdate(99L)).thenReturn(proof);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(claimMapper.selectByIdForUpdate(88L)).thenReturn(claim);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(winner);
        when(playerMapper.selectById(9L)).thenReturn(admin);
        when(playerMapper.selectById(2L)).thenReturn(winner);
        when(taskMapper.selectById(10L)).thenReturn(task);

        service.approveProof(9L, 99L);

        assertEquals(140, winner.getDeposit());
        assertEquals(32, winner.getBounty());
        assertEquals(200, task.getEscrowRemaining());
        assertEquals(1, task.getCompletedCount());
        assertEquals(EventTaskService.COMPLETED, claim.getStatus());
        verify(bountyLedgerMapper).insert(any(PlayerBountyLedger.class));

        BusinessException replay = assertThrows(BusinessException.class,
                () -> service.approveProof(9L, 99L));
        assertTrue(replay.getMessage().contains("不能重复发奖"));
    }

    @Test
    void revokeCompletionRollsBackBothRewardsAndReturnsOneSlot() {
        EventTask task = task(10L, 1L, 100, 25, EventTaskService.TASK_PUBLISHED);
        task.setClaimedCount(3);
        task.setCompletedCount(1);
        task.setEscrowRemaining(200);
        EventTaskClaim claim = claim(88L, 10L, 2L, LocalDateTime.of(2026, 9, 20, 11, 0));
        claim.setStatus(EventTaskService.COMPLETED);
        claim.setCompletedAt(LocalDateTime.of(2026, 9, 20, 11, 45));
        EventTaskProof proof = new EventTaskProof();
        proof.setId(99L);
        proof.setTaskId(10L);
        proof.setClaimId(88L);
        proof.setPlayerId(2L);
        proof.setStatus(EventTaskService.PROOF_STATUS_APPROVED);
        Player winner = player(2L, "完成者", 40, 7);
        Player admin = player(9L, "管理员", 0, 0);

        when(claimMapper.selectById(88L)).thenReturn(claim);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(claimMapper.selectByIdForUpdate(88L)).thenReturn(claim);
        when(proofMapper.selectApprovedByClaimForUpdate(88L)).thenReturn(proof);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(winner);
        when(playerMapper.selectById(9L)).thenReturn(admin);
        when(playerMapper.selectById(2L)).thenReturn(winner);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(proofMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(proof));

        EventTaskDtos.CompletionRevokeRequest request = new EventTaskDtos.CompletionRevokeRequest();
        request.setReason("完成证明复核不通过");
        EventTaskDtos.ClaimVO result = service.revokeCompletion(9L, 88L, request);

        assertEquals(-60, winner.getDeposit());
        assertEquals(-18, winner.getBounty());
        assertEquals(300, task.getEscrowRemaining());
        assertEquals(2, task.getClaimedCount());
        assertEquals(0, task.getCompletedCount());
        assertEquals(EventTaskService.COMPLETION_REVOKED, claim.getStatus());
        assertEquals(EventTaskService.PROOF_STATUS_REVOKED, proof.getStatus());
        assertEquals("完成证明复核不通过", result.getCompletionRevokeReason());
        assertEquals("管理员", result.getCompletionRevokedByName());

        ArgumentCaptor<PlayerDepositLedger> pLedger = ArgumentCaptor.forClass(PlayerDepositLedger.class);
        verify(depositLedgerMapper).insert(pLedger.capture());
        assertEquals("task_reward_reversal", pLedger.getValue().getType());
        assertEquals(-100, pLedger.getValue().getAmount());

        ArgumentCaptor<PlayerBountyLedger> bountyLedger = ArgumentCaptor.forClass(PlayerBountyLedger.class);
        verify(bountyLedgerMapper).insert(bountyLedger.capture());
        assertEquals("task_reward_reversal", bountyLedger.getValue().getType());
        assertEquals(-25, bountyLedger.getValue().getAmount());

        BusinessException replay = assertThrows(BusinessException.class,
                () -> service.revokeCompletion(9L, 88L, request));
        assertTrue(replay.getMessage().contains("尚未撤回"));
        verify(depositLedgerMapper, times(1)).insert(any(PlayerDepositLedger.class));
        verify(bountyLedgerMapper, times(1)).insert(any(PlayerBountyLedger.class));
    }

    @Test
    void completedRecordCannotBeRevokedAfterTaskIsClosed() {
        EventTask task = task(10L, 1L, 100, 25, EventTaskService.TASK_CLOSED);
        EventTaskClaim claim = claim(88L, 10L, 2L, LocalDateTime.of(2026, 9, 20, 11, 0));
        claim.setStatus(EventTaskService.COMPLETED);
        when(claimMapper.selectById(88L)).thenReturn(claim);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(claimMapper.selectByIdForUpdate(88L)).thenReturn(claim);
        EventTaskDtos.CompletionRevokeRequest request = new EventTaskDtos.CompletionRevokeRequest();
        request.setReason("复核不通过");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.revokeCompletion(9L, 88L, request));

        assertTrue(error.getMessage().contains("任务已结束"));
        verify(playerMapper, never()).selectByIdForUpdate(anyLong());
        verify(depositLedgerMapper, never()).insert(any());
        verify(bountyLedgerMapper, never()).insert(any());
    }

    @Test
    void closeTerminatesUnfinishedAndRefundsUnusedEscrow() {
        EventTask task = task(10L, 1L, 100, 25, EventTaskService.TASK_PUBLISHED);
        task.setEscrowRemaining(200);
        EventTaskClaim unfinished = claim(88L, 10L, 2L, LocalDateTime.of(2026, 9, 20, 11, 0));
        Player publisher = player(1L, "发布者", 300, 0);
        Player admin = player(9L, "管理员", 0, 0);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(claimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(unfinished));
        when(claimMapper.selectByIdForUpdate(88L)).thenReturn(unfinished);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(publisher);
        when(playerMapper.selectById(9L)).thenReturn(admin);

        EventTaskDtos.CloseTaskRequest request = new EventTaskDtos.CloseTaskRequest();
        request.setReason("任务目标已达成");
        service.closeTask(9L, 10L, request);

        assertEquals(EventTaskService.TASK_CLOSED, task.getStatus());
        assertEquals(EventTaskService.TERMINATED, unfinished.getStatus());
        assertEquals(500, publisher.getDeposit());
        assertEquals(0, task.getEscrowRemaining());
    }

    @Test
    void editPublishedUpdatesPendingButPreservesCompletedAndAdjustsEscrow() {
        EventTask task = task(10L, 1L, 100, 25, EventTaskService.TASK_PUBLISHED);
        task.setClaimedCount(2);
        task.setCompletedCount(1);
        task.setEscrowRemaining(200);
        task.setAnonymousFeeAmount(50);
        EventTaskClaim pending = claim(88L, 10L, 2L, LocalDateTime.of(2026, 9, 20, 11, 0));
        pending.setStatus(EventTaskService.PROOF_PENDING);
        EventTaskClaim completed = claim(89L, 10L, 3L, LocalDateTime.of(2026, 9, 20, 10, 0));
        completed.setStatus(EventTaskService.COMPLETED);
        completed.setTitleSnapshot("测试任务");
        Player publisher = player(1L, "发布者", 500, 0);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(claimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(pending, completed));
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(publisher);
        when(taskMapper.selectById(10L)).thenReturn(task);
        doAnswer(invocation -> { ((EventTaskReview) invocation.getArgument(0)).setId(123L); return 1; })
                .when(reviewMapper).insert(any(EventTaskReview.class));
        EventTaskDtos.AdminEditRequest request = editRequest(200, 40);
        request.setTitle("更新后的任务");

        service.editPublished(9L, 10L, request);

        assertEquals(300, publisher.getDeposit());
        assertEquals(400, task.getEscrowRemaining());
        assertEquals(500, task.getEscrowTotal());
        assertEquals(200, pending.getPRewardSnapshot());
        assertEquals(40, pending.getBountyRewardSnapshot());
        assertEquals("更新后的任务", pending.getTitleSnapshot());
        assertEquals(100, completed.getPRewardSnapshot());
        assertEquals("测试任务", completed.getTitleSnapshot());
        assertEquals(50, task.getAnonymousFeeAmount());
        ArgumentCaptor<EventTaskReview> review = ArgumentCaptor.forClass(EventTaskReview.class);
        verify(reviewMapper).updateById(review.capture());
        assertTrue(review.getValue().getBeforeSnapshot().contains("测试任务"));
        assertTrue(review.getValue().getAfterSnapshot().contains("更新后的任务"));
        ArgumentCaptor<PlayerDepositLedger> ledger = ArgumentCaptor.forClass(PlayerDepositLedger.class);
        verify(depositLedgerMapper).insert(ledger.capture());
        assertEquals(-200, ledger.getValue().getAmount());
        assertEquals(123L, ledger.getValue().getRefId());
    }

    @Test
    void editPublishedInsufficientBalanceChangesNothing() {
        EventTask task = task(10L, 1L, 100, 25, EventTaskService.TASK_PUBLISHED);
        Player publisher = player(1L, "发布者", 299, 0);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(claimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(publisher);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.editPublished(9L, 10L, editRequest(200, 25)));

        assertTrue(error.getMessage().contains("追加冻结 300P"));
        assertEquals(299, publisher.getDeposit());
        assertEquals(100, task.getPReward());
        assertEquals(300, task.getEscrowRemaining());
        verify(reviewMapper, never()).insert(any());
        verify(taskMapper, never()).updateById(any());
    }

    @Test
    void reducingPublishedRewardRefundsExcessEscrow() {
        EventTask task = task(10L, 1L, 100, 25, EventTaskService.TASK_PUBLISHED);
        Player publisher = player(1L, "发布者", 100, 0);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(claimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(publisher);

        service.editPublished(9L, 10L, editRequest(50, 25));

        assertEquals(250, publisher.getDeposit());
        assertEquals(150, task.getEscrowRemaining());
        assertEquals(150, task.getEscrowTotal());
        ArgumentCaptor<PlayerDepositLedger> ledger = ArgumentCaptor.forClass(PlayerDepositLedger.class);
        verify(depositLedgerMapper).insert(ledger.capture());
        assertEquals(150, ledger.getValue().getAmount());
    }

    @Test
    void officialPublishedEditNeverTouchesPublisherBalance() {
        EventTask task = task(10L, 9L, 100, 25, EventTaskService.TASK_PUBLISHED);
        task.setOfficial(1);
        task.setEscrowRemaining(0);
        task.setEscrowTotal(0);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(claimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.editPublished(9L, 10L, editRequest(200, 30));

        assertEquals(200, task.getPReward());
        assertEquals(0, task.getEscrowRemaining());
        verify(playerMapper, never()).selectByIdForUpdate(anyLong());
        verify(depositLedgerMapper, never()).insert(any());
    }

    @Test
    void revokeCompletedAfterEditReservesCurrentRewardAndRefundsDifference() {
        EventTask task = task(10L, 1L, 50, 25, EventTaskService.TASK_PUBLISHED);
        task.setClaimedCount(1);
        task.setCompletedCount(1);
        task.setEscrowTotal(150);
        task.setEscrowRemaining(50);
        EventTaskClaim completed = claim(88L, 10L, 2L, LocalDateTime.of(2026, 9, 20, 11, 0));
        completed.setStatus(EventTaskService.COMPLETED);
        EventTaskProof proof = new EventTaskProof();
        proof.setId(99L);
        proof.setStatus(EventTaskService.PROOF_STATUS_APPROVED);
        Player winner = player(2L, "完成者", 100, 25);
        Player publisher = player(1L, "发布者", 20, 0);
        when(claimMapper.selectById(88L)).thenReturn(completed);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(claimMapper.selectByIdForUpdate(88L)).thenReturn(completed);
        when(proofMapper.selectApprovedByClaimForUpdate(88L)).thenReturn(proof);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(winner);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(publisher);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(proofMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(proof));
        EventTaskDtos.CompletionRevokeRequest request = new EventTaskDtos.CompletionRevokeRequest();
        request.setReason("复核撤回");

        service.revokeCompletion(9L, 88L, request);

        assertEquals(0, winner.getDeposit());
        assertEquals(70, publisher.getDeposit());
        assertEquals(100, task.getEscrowRemaining());
        assertEquals(100, task.getEscrowTotal());
        assertEquals(EventTaskService.COMPLETION_REVOKED, completed.getStatus());
    }

    @Test
    void cancelClaimRefundsOnceAndVoidsPendingProof() {
        EventTask task = task(10L, 1L, 100, 25, EventTaskService.TASK_PUBLISHED);
        task.setClaimedCount(1);
        EventTaskClaim claim = claim(88L, 10L, 2L, LocalDateTime.of(2026, 9, 20, 11, 0));
        claim.setStatus(EventTaskService.PROOF_PENDING);
        EventTaskProof proof = new EventTaskProof();
        proof.setId(99L);
        proof.setStatus(EventTaskService.PROOF_STATUS_PENDING);
        Player claimant = player(2L, "接取者", 10, 0);
        when(claimMapper.selectById(88L)).thenReturn(claim);
        when(claimMapper.selectByIdForUpdate(88L)).thenReturn(claim);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(claimant);
        when(proofMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(proof));
        when(taskMapper.selectById(10L)).thenReturn(task);
        EventTaskDtos.AdminCancelClaimRequest request = new EventTaskDtos.AdminCancelClaimRequest();
        request.setReason("任务条件已变更");

        EventTaskDtos.ClaimVO result = service.cancelClaim(9L, 88L, request);

        assertEquals(60, claimant.getDeposit());
        assertEquals(0, task.getClaimedCount());
        assertEquals(EventTaskService.ADMIN_CANCELLED, result.getStatus());
        assertEquals(LocalDateTime.of(2026, 9, 20, 13, 0), result.getReclaimAvailableAt());
        assertEquals(EventTaskService.PROOF_STATUS_VOIDED, proof.getStatus());
        verify(adminAssetService).recordReversal(eq(50), eq("task_claim_fee_admin_refund"), any(),
                eq("event_task"), eq("event_task_claims"), eq(88L), isNull(), isNull(), any());
        assertThrows(BusinessException.class, () -> service.cancelClaim(9L, 88L, request));
        verify(depositLedgerMapper, times(1)).insert(any());
    }

    @Test
    void adminCancelledClaimNeedsFullHourThenCreatesNewHistoryRow() {
        EventTask task = task(10L, 1L, 150, 30, EventTaskService.TASK_PUBLISHED);
        EventTaskClaim old = claim(88L, 10L, 2L, LocalDateTime.of(2026, 9, 20, 10, 0));
        old.setStatus(EventTaskService.ADMIN_CANCELLED);
        old.setTerminatedAt(LocalDateTime.of(2026, 9, 20, 11, 0, 1));
        Player claimant = player(2L, "接取者", 100, 0);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(claimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(old));

        assertThrows(BusinessException.class, () -> service.claim(2L, 10L));
        verify(claimMapper, never()).insert(any());

        old.setTerminatedAt(LocalDateTime.of(2026, 9, 20, 11, 0, 0));
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(claimant);
        when(taskMapper.selectById(10L)).thenReturn(task);
        ArgumentCaptor<EventTaskClaim> inserted = ArgumentCaptor.forClass(EventTaskClaim.class);
        service.claim(2L, 10L);

        verify(claimMapper).insert(inserted.capture());
        assertNotSame(old, inserted.getValue());
        assertEquals(88L, old.getId());
        assertEquals(EventTaskService.ADMIN_CANCELLED, old.getStatus());
        assertEquals(150, inserted.getValue().getPRewardSnapshot());
        assertEquals(50, claimant.getDeposit());
    }

    @Test
    void voluntarilyAbandonedClaimStillCannotBeClaimedAgain() {
        EventTask task = task(10L, 1L, 100, 25, EventTaskService.TASK_PUBLISHED);
        EventTaskClaim old = claim(88L, 10L, 2L, LocalDateTime.of(2026, 9, 20, 10, 0));
        old.setStatus(EventTaskService.CLAIM_ABANDONED);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(claimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(old));

        assertThrows(BusinessException.class, () -> service.claim(2L, 10L));
        verify(claimMapper, never()).insert(any());
    }

    private static EventTaskDtos.AdminEditRequest editRequest(int pReward, int bountyReward) {
        EventTaskDtos.AdminEditRequest request = new EventTaskDtos.AdminEditRequest();
        request.setTitle("测试任务");
        request.setRequirements("完成指定目标并截图");
        request.setPReward(pReward);
        request.setBountyReward(bountyReward);
        return request;
    }

    private static EventTask task(Long id, Long publisherId, int pReward, int bountyReward, String status) {
        EventTask task = new EventTask();
        task.setId(id);
        task.setSeason("s2");
        task.setPublisherPlayerId(publisherId);
        task.setPublisherNameSnapshot("发布者");
        task.setOfficial(0);
        task.setAnonymous(0);
        task.setTitle("测试任务");
        task.setRequirements("完成指定目标并截图");
        task.setPReward(pReward);
        task.setBountyReward(bountyReward);
        task.setClaimFee(50);
        task.setMaxClaimants(3);
        task.setClaimedCount(0);
        task.setCompletedCount(0);
        task.setEscrowTotal(300);
        task.setEscrowRemaining(300);
        task.setStatus(status);
        return task;
    }

    private static EventTaskClaim claim(Long id, Long taskId, Long playerId, LocalDateTime claimedAt) {
        EventTaskClaim claim = new EventTaskClaim();
        claim.setId(id);
        claim.setTaskId(taskId);
        claim.setPlayerId(playerId);
        claim.setStatus(EventTaskService.CLAIMED);
        claim.setFeeAmount(50);
        claim.setPRewardSnapshot(100);
        claim.setBountyRewardSnapshot(25);
        claim.setClaimedAt(claimedAt);
        claim.setAbandonRefunded(0);
        return claim;
    }

    private static Player player(Long id, String name, int deposit, int bounty) {
        Player player = new Player();
        player.setId(id);
        player.setName(name);
        player.setDeposit(deposit);
        player.setBounty(bounty);
        player.setStatus(1);
        player.setDeleted(0);
        return player;
    }
}
