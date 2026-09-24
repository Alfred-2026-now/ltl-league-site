package com.ltl.league.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltl.league.admin.service.AdminAssetService;
import com.ltl.league.admin.service.RuleParameterService;
import com.ltl.league.dto.EventTaskDtos;
import com.ltl.league.entity.*;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.*;
import com.ltl.league.util.ImageCompressUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EventTaskService {
    public static final String TASK_PENDING = "PENDING_REVIEW";
    public static final String TASK_RETURNED = "RETURNED";
    public static final String TASK_PUBLISHED = "PUBLISHED";
    public static final String TASK_CLOSED = "CLOSED";
    public static final String TASK_ABANDONED = "ABANDONED";

    public static final String CLAIMED = "CLAIMED";
    public static final String PROOF_PENDING = "PROOF_PENDING";
    public static final String PROOF_RETURNED = "PROOF_RETURNED";
    public static final String COMPLETED = "COMPLETED";
    public static final String COMPLETION_REVOKED = "COMPLETION_REVOKED";
    public static final String CLAIM_ABANDONED = "ABANDONED";
    public static final String TERMINATED = "TERMINATED";
    public static final String ADMIN_CANCELLED = "ADMIN_CANCELLED";

    public static final String PROOF_STATUS_PENDING = "PENDING";
    public static final String PROOF_STATUS_RETURNED = "RETURNED";
    public static final String PROOF_STATUS_APPROVED = "APPROVED";
    public static final String PROOF_STATUS_REVOKED = "REVOKED";
    public static final String PROOF_STATUS_VOIDED = "VOIDED";

    private static final int MAX_CLAIMANTS = 100;
    private static final int MAX_REWARD = 10_000_000;
    private static final int ANONYMOUS_MINIMUM_FEE = 50;
    private static final String ANONYMOUS_FEE_RATE_KEY = "event_task.anonymous_fee_rate";
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper();

    private final EventTaskMapper taskMapper;
    private final EventTaskReviewMapper reviewMapper;
    private final EventTaskClaimMapper claimMapper;
    private final EventTaskProofMapper proofMapper;
    private final EventTaskProofImageMapper proofImageMapper;
    private final PlayerMapper playerMapper;
    private final PlayerDepositLedgerMapper depositLedgerMapper;
    private final PlayerBountyLedgerMapper bountyLedgerMapper;
    private final AdminAssetService adminAssetService;
    private final RuleParameterService ruleParameterService;
    private final Clock clock;

    @Value("${ltl.league.current-season:s1}")
    private String currentSeason;

    @Value("${ltl.upload.dir:/var/www/ltl-league/uploads}")
    private String uploadDir;

    @Value("${ltl.upload.url-prefix:/uploads}")
    private String uploadUrlPrefix;

    public EventTaskService(
            EventTaskMapper taskMapper,
            EventTaskReviewMapper reviewMapper,
            EventTaskClaimMapper claimMapper,
            EventTaskProofMapper proofMapper,
            EventTaskProofImageMapper proofImageMapper,
            PlayerMapper playerMapper,
            PlayerDepositLedgerMapper depositLedgerMapper,
            PlayerBountyLedgerMapper bountyLedgerMapper,
            AdminAssetService adminAssetService,
            RuleParameterService ruleParameterService,
            Clock clock) {
        this.taskMapper = taskMapper;
        this.reviewMapper = reviewMapper;
        this.claimMapper = claimMapper;
        this.proofMapper = proofMapper;
        this.proofImageMapper = proofImageMapper;
        this.playerMapper = playerMapper;
        this.depositLedgerMapper = depositLedgerMapper;
        this.bountyLedgerMapper = bountyLedgerMapper;
        this.adminAssetService = adminAssetService;
        this.ruleParameterService = ruleParameterService;
        this.clock = clock;
    }

    public EventTaskDtos.TaskPublicSettingsVO getPublicSettings() {
        EventTaskDtos.TaskPublicSettingsVO settings = new EventTaskDtos.TaskPublicSettingsVO();
        settings.setAnonymousMinimumFee(ANONYMOUS_MINIMUM_FEE);
        settings.setAnonymousFeeRate(anonymousFeeRate());
        return settings;
    }

    public List<EventTaskDtos.TaskVO> listPublic(Player viewer) {
        List<EventTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<EventTask>()
                .eq(EventTask::getSeason, currentSeason)
                .eq(EventTask::getStatus, TASK_PUBLISHED)
                .eq(EventTask::getDeleted, 0)
                .orderByDesc(EventTask::getPublishedAt)
                .orderByDesc(EventTask::getId));
        return toTaskVOs(tasks, viewer, false);
    }

    public EventTaskDtos.TaskVO getPublicDetail(Long taskId, Player viewer) {
        EventTask task = requireTask(taskId);
        boolean owner = viewer != null && Objects.equals(viewer.getId(), task.getPublisherPlayerId());
        if (!TASK_PUBLISHED.equals(task.getStatus()) && !owner) {
            throw new BusinessException(404, "任务不存在或尚未公开");
        }
        return toTaskVO(task, viewer, false);
    }

    public List<EventTaskDtos.TaskVO> listPublishedBy(Long playerId) {
        List<EventTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<EventTask>()
                .eq(EventTask::getPublisherPlayerId, playerId)
                .eq(EventTask::getSeason, currentSeason)
                .eq(EventTask::getDeleted, 0)
                .orderByDesc(EventTask::getCreatedAt));
        Player viewer = playerMapper.selectById(playerId);
        return toTaskVOs(tasks, viewer, false);
    }

    public List<EventTaskDtos.ClaimVO> listClaimedBy(Long playerId) {
        List<EventTaskClaim> claims = claimMapper.selectList(new LambdaQueryWrapper<EventTaskClaim>()
                .eq(EventTaskClaim::getPlayerId, playerId)
                .eq(EventTaskClaim::getDeleted, 0)
                .orderByDesc(EventTaskClaim::getClaimedAt));
        return claims.stream().map(claim -> toClaimVO(claim, true)).collect(Collectors.toList());
    }

    @Transactional
    public EventTaskDtos.TaskVO create(Long publisherId, EventTaskDtos.TaskWriteRequest request) {
        Player publisher = requirePlayer(publisherId);
        validateTaskWrite(request);
        EventTask task = new EventTask();
        task.setSeason(currentSeason);
        task.setPublisherPlayerId(publisher.getId());
        task.setPublisherNameSnapshot(publisher.getName());
        task.setOfficial(0);
        applyWrite(task, request);
        task.setAnonymousFeeAmount(0);
        task.setClaimedCount(0);
        task.setCompletedCount(0);
        task.setEscrowTotal(0);
        task.setEscrowRemaining(0);
        task.setStatus(TASK_PENDING);
        taskMapper.insert(task);
        addReview(task.getId(), "SUBMIT", publisherId, "提交任务审核", null, null);
        return toTaskVO(task, publisher, true);
    }

    @Transactional
    public EventTaskDtos.TaskVO updateReturned(Long publisherId, Long taskId, EventTaskDtos.TaskWriteRequest request) {
        validateTaskWrite(request);
        EventTask task = taskMapper.selectByIdForUpdate(taskId);
        requireOwner(task, publisherId);
        if (!TASK_RETURNED.equals(task.getStatus())) {
            throw new BusinessException(409, "只有已打回的任务可以修改");
        }
        applyWrite(task, request);
        taskMapper.updateById(task);
        return toTaskVO(task, playerMapper.selectById(publisherId), true);
    }

    @Transactional
    public EventTaskDtos.TaskVO resubmit(Long publisherId, Long taskId) {
        EventTask task = taskMapper.selectByIdForUpdate(taskId);
        requireOwner(task, publisherId);
        if (!TASK_RETURNED.equals(task.getStatus())) {
            throw new BusinessException(409, "只有已打回的任务可以重新投送");
        }
        task.setStatus(TASK_PENDING);
        task.setLatestReviewComment(null);
        taskMapper.updateById(task);
        addReview(taskId, "RESUBMIT", publisherId, "重新提交审核", null, null);
        return toTaskVO(task, playerMapper.selectById(publisherId), true);
    }

    @Transactional
    public void abandonPublication(Long publisherId, Long taskId) {
        EventTask task = taskMapper.selectByIdForUpdate(taskId);
        requireOwner(task, publisherId);
        if (!TASK_PENDING.equals(task.getStatus()) && !TASK_RETURNED.equals(task.getStatus())) {
            throw new BusinessException(409, "当前状态不能放弃投送");
        }
        task.setStatus(TASK_ABANDONED);
        taskMapper.updateById(task);
        addReview(taskId, "ABANDON", publisherId, "发布者放弃投送", null, null);
    }

    @Transactional
    public EventTaskDtos.ClaimVO claim(Long playerId, Long taskId) {
        EventTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || !TASK_PUBLISHED.equals(task.getStatus())) {
            throw new BusinessException(409, "任务已停止接取");
        }
        if (Objects.equals(task.getPublisherPlayerId(), playerId)) {
            throw new BusinessException(400, "不能接取自己发布的任务");
        }
        if (task.getMaxClaimants() == null || safe(task.getClaimedCount()) >= task.getMaxClaimants()) {
            throw new BusinessException(409, "任务接取名额已满");
        }
        List<EventTaskClaim> previous = claimMapper.selectList(new LambdaQueryWrapper<EventTaskClaim>()
                .eq(EventTaskClaim::getTaskId, taskId)
                .eq(EventTaskClaim::getPlayerId, playerId)
                .eq(EventTaskClaim::getDeleted, 0)
                .orderByDesc(EventTaskClaim::getId));
        for (EventTaskClaim old : previous) {
            if (!ADMIN_CANCELLED.equals(old.getStatus())) {
                throw new BusinessException(409, "你已经接取或放弃过该任务，不能重复接取");
            }
            if (old.getTerminatedAt() == null || now().isBefore(old.getTerminatedAt().plusHours(1))) {
                throw new BusinessException(409, "管理员取消接取后需等待1小时才能再次接取");
            }
        }

        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null) {
            throw new BusinessException(404, "选手不存在");
        }
        int fee = safe(task.getClaimFee());
        int before = safe(player.getDeposit());
        if (before < fee) {
            throw new BusinessException(400, "个人P币不足，无法接取任务");
        }

        LocalDateTime now = now();
        EventTaskClaim claim = new EventTaskClaim();
        claim.setTaskId(taskId);
        claim.setPlayerId(playerId);
        claim.setStatus(CLAIMED);
        claim.setFeeAmount(fee);
        claim.setPRewardSnapshot(safe(task.getPReward()));
        claim.setBountyRewardSnapshot(safe(task.getBountyReward()));
        claim.setTitleSnapshot(task.getTitle());
        claim.setRequirementsSnapshot(task.getRequirements());
        claim.setClaimedAt(now);
        claim.setAbandonRefunded(0);
        claimMapper.insert(claim);

        if (fee > 0) {
            player.setDeposit(before - fee);
            playerMapper.updateById(player);
            addDepositLedger(player, "task_claim_fee", -fee,
                    "接取任务：" + task.getTitle(), before, before - fee,
                    "event_task", "event_task_claims", claim.getId(), "system");
            adminAssetService.recordIncome(fee, "task_claim_fee", "任务接取费用：" + task.getTitle(),
                    "event_task", "event_task_claims", claim.getId(), null, null, "system");
        }

        task.setClaimedCount(safe(task.getClaimedCount()) + 1);
        taskMapper.updateById(task);
        return toClaimVO(claim, true);
    }

    @Transactional
    public EventTaskDtos.ClaimVO abandonClaim(Long playerId, Long claimId) {
        EventTaskClaim snapshot = claimMapper.selectById(claimId);
        if (snapshot == null || !Objects.equals(snapshot.getPlayerId(), playerId)) {
            throw new BusinessException(404, "接取记录不存在");
        }
        EventTask task = taskMapper.selectByIdForUpdate(snapshot.getTaskId());
        EventTaskClaim claim = claimMapper.selectByIdForUpdate(claimId);
        if (claim == null || !Objects.equals(claim.getPlayerId(), playerId)) {
            throw new BusinessException(404, "接取记录不存在");
        }
        if (!List.of(CLAIMED, PROOF_PENDING, PROOF_RETURNED).contains(claim.getStatus())) {
            throw new BusinessException(409, "当前接取状态不能放弃");
        }
        if (task == null || !TASK_PUBLISHED.equals(task.getStatus())) {
            throw new BusinessException(409, "任务已注销，不能主动放弃或申请退款");
        }

        LocalDateTime now = now();
        boolean refund = !now.isAfter(claim.getClaimedAt().plusMinutes(30));
        int fee = safe(claim.getFeeAmount());
        if (refund && fee > 0) {
            Player player = playerMapper.selectByIdForUpdate(playerId);
            int before = safe(player.getDeposit());
            int after = checkedAdd(before, fee, "接取费退款");
            player.setDeposit(after);
            playerMapper.updateById(player);
            addDepositLedger(player, "task_claim_fee_refund", fee,
                    "接取后30分钟内放弃任务：" + task.getTitle(), before, after,
                    "event_task", "event_task_claims", claim.getId(), "system");
            adminAssetService.recordReversal(fee, "task_claim_fee_refund", "30分钟内放弃任务，退还接取费",
                    "event_task", "event_task_claims", claim.getId(), null, null, "system");
        }

        claim.setStatus(CLAIM_ABANDONED);
        claim.setAbandonedAt(now);
        claim.setAbandonRefunded(refund ? 1 : 0);
        claimMapper.updateById(claim);
        task.setClaimedCount(Math.max(0, safe(task.getClaimedCount()) - 1));
        taskMapper.updateById(task);
        voidPendingProofs(claim.getId(), "选手主动放弃任务");
        return toClaimVO(claim, true);
    }

    @Transactional
    public EventTaskDtos.ProofVO submitProof(Long playerId, Long claimId, String description, List<MultipartFile> files) {
        EventTaskClaim snapshot = claimMapper.selectById(claimId);
        if (snapshot == null || !Objects.equals(snapshot.getPlayerId(), playerId)) {
            throw new BusinessException(404, "接取记录不存在");
        }
        EventTask task = taskMapper.selectByIdForUpdate(snapshot.getTaskId());
        EventTaskClaim claim = claimMapper.selectByIdForUpdate(claimId);
        if (claim == null || !Objects.equals(claim.getPlayerId(), playerId)) {
            throw new BusinessException(404, "接取记录不存在");
        }
        if (!CLAIMED.equals(claim.getStatus()) && !PROOF_RETURNED.equals(claim.getStatus())) {
            throw new BusinessException(409, "当前状态不能提交完成证明");
        }
        if (task == null || !TASK_PUBLISHED.equals(task.getStatus())) {
            throw new BusinessException(409, "任务已注销，不能提交证明");
        }
        validateProof(description, files);
        int attempt = nextAttempt(claimId);
        EventTaskProof proof = new EventTaskProof();
        proof.setTaskId(task.getId());
        proof.setClaimId(claimId);
        proof.setPlayerId(playerId);
        proof.setAttemptNo(attempt);
        proof.setDescription(trimToNull(description));
        proof.setStatus(PROOF_STATUS_PENDING);
        proofMapper.insert(proof);

        List<Path> created = new ArrayList<>();
        try {
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                String extension = detectImageExtension(file);
                Path dir = Path.of(uploadDir, "tasks", String.valueOf(task.getId()), String.valueOf(claimId), String.valueOf(proof.getId()));
                Files.createDirectories(dir);
                Path stored = dir.resolve(UUID.randomUUID() + "." + extension);
                file.transferTo(stored);
                created.add(stored);
                Path finalPath = "webp".equals(extension) ? stored : ImageCompressUtil.compressUploadedFile(stored);
                if (!finalPath.equals(stored)) {
                    created.add(finalPath);
                }
                EventTaskProofImage image = new EventTaskProofImage();
                image.setProofId(proof.getId());
                image.setTaskId(task.getId());
                image.setClaimId(claimId);
                image.setLabel("完成证明 " + (i + 1));
                image.setFilePath(finalPath.toString());
                image.setUrl(normalizePrefix(uploadUrlPrefix) + "/tasks/" + task.getId() + "/" + claimId + "/" + proof.getId() + "/" + finalPath.getFileName());
                proofImageMapper.insert(image);
            }
        } catch (Exception e) {
            created.forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
            throw new BusinessException(500, "保存任务证明失败：" + e.getMessage());
        }

        claim.setStatus(PROOF_PENDING);
        claim.setProofSubmittedAt(now());
        claimMapper.updateById(claim);
        return toProofVO(proof);
    }

    public List<EventTaskDtos.TaskVO> listAdminTasks(String status, Player admin) {
        LambdaQueryWrapper<EventTask> query = new LambdaQueryWrapper<EventTask>()
                .eq(EventTask::getSeason, currentSeason)
                .eq(EventTask::getDeleted, 0)
                .eq(status != null && !status.isBlank(), EventTask::getStatus, status)
                .orderByDesc(EventTask::getCreatedAt);
        return toTaskVOs(taskMapper.selectList(query), admin, true);
    }

    public List<EventTaskDtos.ClaimVO> listAdminClaimHistory() {
        return claimMapper.selectList(new LambdaQueryWrapper<EventTaskClaim>()
                        .eq(EventTaskClaim::getDeleted, 0)
                        .orderByDesc(EventTaskClaim::getClaimedAt)
                        .orderByDesc(EventTaskClaim::getId))
                .stream().map(claim -> toClaimVO(claim, false)).collect(Collectors.toList());
    }

    public List<EventTaskDtos.ProofVO> listPendingProofs() {
        return proofMapper.selectList(new LambdaQueryWrapper<EventTaskProof>()
                        .eq(EventTaskProof::getStatus, PROOF_STATUS_PENDING)
                        .eq(EventTaskProof::getDeleted, 0)
                        .orderByAsc(EventTaskProof::getCreatedAt))
                .stream().map(this::toProofVO).collect(Collectors.toList());
    }

    @Transactional
    public void returnTask(Long adminId, Long taskId, EventTaskDtos.AdminReturnRequest request) {
        String comment = requireComment(request == null ? null : request.getComment());
        EventTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || !TASK_PENDING.equals(task.getStatus())) {
            throw new BusinessException(409, "只有待审核任务可以打回");
        }
        task.setStatus(TASK_RETURNED);
        task.setLatestReviewComment(comment);
        task.setReviewedByPlayerId(adminId);
        task.setReviewedAt(now());
        taskMapper.updateById(task);
        addReview(taskId, "RETURN", adminId, comment, null, null);
    }

    @Transactional
    public EventTaskDtos.TaskVO publishTask(Long adminId, Long taskId, EventTaskDtos.AdminPublishRequest request) {
        validateAdminConfig(request == null ? null : request.getClaimFee(), request == null ? null : request.getMaxClaimants());
        EventTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || !TASK_PENDING.equals(task.getStatus())) {
            throw new BusinessException(409, "只有待审核任务可以发布");
        }
        int escrow;
        try {
            escrow = Math.multiplyExact(safe(task.getPReward()), request.getMaxClaimants());
        } catch (ArithmeticException e) {
            throw new BusinessException(400, "P币悬赏总额过大");
        }
        boolean anonymous = Integer.valueOf(1).equals(task.getAnonymous());
        int anonymousRate = anonymous ? anonymousFeeRate() : 0;
        int anonymousFee = anonymous ? calculateAnonymousFee(escrow, anonymousRate) : 0;
        int required = checkedAdd(escrow, anonymousFee, "发布任务所需P币");
        Player publisher = playerMapper.selectByIdForUpdate(task.getPublisherPlayerId());
        if (publisher == null) {
            throw new BusinessException(404, "任务发布者不存在");
        }
        int before = safe(publisher.getDeposit());
        if (before < required) {
            String feeText = anonymousFee > 0 ? "并支付匿名发布费 " + anonymousFee + "P" : "";
            throw new BusinessException(400, "发布者个人P币不足，当前 " + before + "P，需要冻结 " + escrow + "P" + feeText);
        }
        int balance = before;
        if (escrow > 0) {
            int afterEscrow = balance - escrow;
            addDepositLedger(publisher, "task_reward_escrow", -escrow,
                    "任务悬赏冻结：" + task.getTitle(), balance, afterEscrow,
                    "event_task", "event_tasks", task.getId(), playerName(adminId));
            balance = afterEscrow;
        }
        if (anonymousFee > 0) {
            int afterFee = balance - anonymousFee;
            addDepositLedger(publisher, "task_anonymous_fee", -anonymousFee,
                    "任务匿名发布服务费：" + task.getTitle(), balance, afterFee,
                    "event_task", "event_tasks", task.getId(), playerName(adminId));
            adminAssetService.recordIncome(anonymousFee, "task_anonymous_fee",
                    "任务匿名发布服务费：" + task.getTitle(), "event_task", "event_tasks", task.getId(),
                    null, null, playerName(adminId));
            balance = afterFee;
        }
        if (required > 0) {
            publisher.setDeposit(balance);
            playerMapper.updateById(publisher);
        }
        LocalDateTime now = now();
        task.setClaimFee(request.getClaimFee());
        task.setMaxClaimants(request.getMaxClaimants());
        task.setEscrowTotal(escrow);
        task.setEscrowRemaining(escrow);
        task.setAnonymousFeeRateSnapshot(anonymous ? anonymousRate : null);
        task.setAnonymousFeeAmount(anonymousFee);
        task.setStatus(TASK_PUBLISHED);
        task.setLatestReviewComment(null);
        task.setReviewedByPlayerId(adminId);
        task.setReviewedAt(now);
        task.setPublishedAt(now);
        taskMapper.updateById(task);
        addReview(taskId, "PUBLISH", adminId,
                anonymous ? "审核通过并发布；匿名发布费 " + anonymousFee + "P" : "审核通过并发布",
                request.getClaimFee(), request.getMaxClaimants());
        return toTaskVO(task, playerMapper.selectById(adminId), true);
    }

    @Transactional
    public EventTaskDtos.TaskVO publishOfficial(Long adminId, EventTaskDtos.OfficialTaskRequest request) {
        validateTaskWrite(request);
        validateAdminConfig(request.getClaimFee(), request.getMaxClaimants());
        Player admin = requirePlayer(adminId);
        LocalDateTime now = now();
        EventTask task = new EventTask();
        task.setSeason(currentSeason);
        task.setPublisherPlayerId(adminId);
        task.setPublisherNameSnapshot(admin.getName());
        task.setOfficial(1);
        applyWrite(task, request);
        task.setAnonymous(0);
        task.setAnonymousFeeRateSnapshot(null);
        task.setAnonymousFeeAmount(0);
        task.setClaimFee(request.getClaimFee());
        task.setMaxClaimants(request.getMaxClaimants());
        task.setClaimedCount(0);
        task.setCompletedCount(0);
        task.setEscrowTotal(0);
        task.setEscrowRemaining(0);
        task.setStatus(TASK_PUBLISHED);
        task.setReviewedByPlayerId(adminId);
        task.setReviewedAt(now);
        task.setPublishedAt(now);
        taskMapper.insert(task);
        addReview(task.getId(), "OFFICIAL_PUBLISH", adminId, "管理员无成本发布官方任务", request.getClaimFee(), request.getMaxClaimants());
        return toTaskVO(task, admin, true);
    }

    @Transactional
    public EventTaskDtos.TaskVO editPublished(Long adminId, Long taskId, EventTaskDtos.AdminEditRequest request) {
        validateTaskWrite(request);
        EventTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || !TASK_PUBLISHED.equals(task.getStatus())) {
            throw new BusinessException(409, "只有进行中的任务可以编辑");
        }
        List<EventTaskClaim> claims = claimMapper.selectList(new LambdaQueryWrapper<EventTaskClaim>()
                .eq(EventTaskClaim::getTaskId, taskId)
                .eq(EventTaskClaim::getDeleted, 0));
        long completed = claims.stream().filter(claim -> COMPLETED.equals(claim.getStatus())).count();
        long active = claims.stream().filter(claim -> List.of(CLAIMED, PROOF_PENDING, PROOF_RETURNED).contains(claim.getStatus())).count();
        if (completed != safe(task.getCompletedCount()) || completed + active != safe(task.getClaimedCount())
                || task.getMaxClaimants() == null || completed > task.getMaxClaimants()) {
            throw new BusinessException(409, "任务接取人数记录异常，无法编辑");
        }
        int newRemaining;
        try {
            newRemaining = Math.multiplyExact(request.getPReward(), task.getMaxClaimants() - (int) completed);
        } catch (ArithmeticException ex) {
            throw new BusinessException(400, "P币悬赏总额过大");
        }
        int delta = newRemaining - safe(task.getEscrowRemaining());
        int newTotal = checkedAdd(safe(task.getEscrowTotal()), delta, "任务冻结P币总额");
        if (newTotal < newRemaining || newTotal < 0) {
            throw new BusinessException(409, "任务冻结P币记录异常，无法编辑");
        }
        boolean official = Integer.valueOf(1).equals(task.getOfficial());
        Player publisher = null;
        if (!official && delta != 0) {
            publisher = playerMapper.selectByIdForUpdate(task.getPublisherPlayerId());
            if (publisher == null) {
                throw new BusinessException(404, "任务发布者不存在");
            }
            if (delta > 0 && safe(publisher.getDeposit()) < delta) {
                throw new BusinessException(400, "发布者个人P币不足，需要追加冻结 " + delta + "P");
            }
            checkedAdd(safe(publisher.getDeposit()), -delta, "发布者个人P币");
        }

        String before = taskSnapshot(task);
        task.setTitle(request.getTitle().trim());
        task.setRequirements(request.getRequirements().trim());
        task.setBudgetNote(trimToNull(request.getBudgetNote()));
        task.setPReward(request.getPReward());
        task.setBountyReward(request.getBountyReward());
        if (!official) {
            task.setEscrowRemaining(newRemaining);
            task.setEscrowTotal(newTotal);
        }
        EventTaskReview review = addReview(taskId, "EDIT", adminId, "管理员编辑已发布任务",
                task.getClaimFee(), task.getMaxClaimants());
        review.setBeforeSnapshot(before);
        review.setAfterSnapshot(taskSnapshot(task));
        reviewMapper.updateById(review);
        if (publisher != null) {
            int balanceBefore = safe(publisher.getDeposit());
            int balanceAfter = balanceBefore - delta;
            publisher.setDeposit(balanceAfter);
            playerMapper.updateById(publisher);
            addDepositLedger(publisher, delta > 0 ? "task_reward_escrow_adjust" : "task_reward_escrow_adjust_refund",
                    -delta, "任务奖励调整：" + task.getTitle(), balanceBefore, balanceAfter,
                    "event_task", "event_task_reviews", review.getId(), playerName(adminId));
        }
        for (EventTaskClaim claim : claims) {
            if (List.of(CLAIMED, PROOF_PENDING, PROOF_RETURNED).contains(claim.getStatus())) {
                claim.setPRewardSnapshot(request.getPReward());
                claim.setBountyRewardSnapshot(request.getBountyReward());
                claim.setTitleSnapshot(task.getTitle());
                claim.setRequirementsSnapshot(task.getRequirements());
                claimMapper.updateById(claim);
            }
        }
        taskMapper.updateById(task);
        return toTaskVO(task, playerMapper.selectById(adminId), true);
    }

    @Transactional
    public EventTaskDtos.ClaimVO cancelClaim(Long adminId, Long claimId, EventTaskDtos.AdminCancelClaimRequest request) {
        String reason = requireComment(request == null ? null : request.getReason());
        EventTaskClaim snapshot = claimMapper.selectById(claimId);
        if (snapshot == null) {
            throw new BusinessException(404, "接取记录不存在");
        }
        EventTask task = taskMapper.selectByIdForUpdate(snapshot.getTaskId());
        EventTaskClaim claim = claimMapper.selectByIdForUpdate(claimId);
        if (task == null || !TASK_PUBLISHED.equals(task.getStatus())) {
            throw new BusinessException(409, "只有进行中的任务可取消接取");
        }
        if (claim == null || !List.of(CLAIMED, PROOF_PENDING, PROOF_RETURNED).contains(claim.getStatus())) {
            throw new BusinessException(409, "该接取已处理，不能重复取消或退款");
        }
        int fee = safe(claim.getFeeAmount());
        if (fee > 0) {
            Player player = playerMapper.selectByIdForUpdate(claim.getPlayerId());
            if (player == null) {
                throw new BusinessException(404, "接取者不存在，无法退款");
            }
            int before = safe(player.getDeposit());
            int after = checkedAdd(before, fee, "接取费退款");
            player.setDeposit(after);
            playerMapper.updateById(player);
            addDepositLedger(player, "task_claim_fee_admin_refund", fee,
                    "管理员取消接取并退费：" + task.getTitle(), before, after,
                    "event_task", "event_task_claims", claimId, playerName(adminId));
            adminAssetService.recordReversal(fee, "task_claim_fee_admin_refund", "管理员取消接取，退还接取费",
                    "event_task", "event_task_claims", claimId, null, null, playerName(adminId));
        }
        int claimedCount = safe(task.getClaimedCount());
        if (claimedCount <= 0) {
            throw new BusinessException(409, "任务接取人数记录异常，无法取消");
        }
        LocalDateTime now = now();
        claim.setStatus(ADMIN_CANCELLED);
        claim.setTerminatedAt(now);
        claim.setAdminCancelReason(reason);
        claim.setAbandonRefunded(1);
        claimMapper.updateById(claim);
        task.setClaimedCount(claimedCount - 1);
        taskMapper.updateById(task);
        voidPendingProofs(claimId, "管理员取消接取");
        addReview(task.getId(), "CANCEL_CLAIM", adminId, "取消接取 #" + claimId,
                task.getClaimFee(), task.getMaxClaimants());
        return toClaimVO(claim, true);
    }

    @Transactional
    public void returnProof(Long adminId, Long proofId, EventTaskDtos.ProofReviewRequest request) {
        String comment = requireComment(request == null ? null : request.getComment());
        EventTaskProof snapshot = proofMapper.selectById(proofId);
        if (snapshot == null) {
            throw new BusinessException(404, "证明不存在");
        }
        EventTask task = taskMapper.selectByIdForUpdate(snapshot.getTaskId());
        EventTaskClaim claim = claimMapper.selectByIdForUpdate(snapshot.getClaimId());
        EventTaskProof proof = proofMapper.selectByIdForUpdate(proofId);
        if (proof == null || !PROOF_STATUS_PENDING.equals(proof.getStatus())) {
            throw new BusinessException(409, "只有待审核证明可以打回");
        }
        if (task == null || !TASK_PUBLISHED.equals(task.getStatus())) {
            throw new BusinessException(409, "任务已注销，不能审核证明");
        }
        if (claim == null || !PROOF_PENDING.equals(claim.getStatus())) {
            throw new BusinessException(409, "接取记录状态已经改变");
        }
        proof.setStatus(PROOF_STATUS_RETURNED);
        proof.setReviewComment(comment);
        proof.setReviewedByPlayerId(adminId);
        proof.setReviewedAt(now());
        proofMapper.updateById(proof);
        claim.setStatus(PROOF_RETURNED);
        claimMapper.updateById(claim);
    }

    @Transactional
    public EventTaskDtos.ClaimVO approveProof(Long adminId, Long proofId) {
        EventTaskProof snapshot = proofMapper.selectById(proofId);
        if (snapshot == null) {
            throw new BusinessException(404, "证明不存在");
        }
        EventTask task = taskMapper.selectByIdForUpdate(snapshot.getTaskId());
        EventTaskClaim claim = claimMapper.selectByIdForUpdate(snapshot.getClaimId());
        EventTaskProof proof = proofMapper.selectByIdForUpdate(proofId);
        if (proof == null || !PROOF_STATUS_PENDING.equals(proof.getStatus())) {
            throw new BusinessException(409, "该证明已处理，不能重复发奖");
        }
        if (task == null || !TASK_PUBLISHED.equals(task.getStatus())) {
            throw new BusinessException(409, "任务已注销，不能审核证明");
        }
        if (claim == null || !PROOF_PENDING.equals(claim.getStatus())) {
            throw new BusinessException(409, "接取记录已处理，不能重复发奖");
        }
        Player winner = playerMapper.selectByIdForUpdate(claim.getPlayerId());
        if (winner == null) {
            throw new BusinessException(404, "获奖选手不存在");
        }

        int pReward = safe(claim.getPRewardSnapshot());
        if (!Integer.valueOf(1).equals(task.getOfficial())) {
            if (safe(task.getEscrowRemaining()) < pReward) {
                throw new BusinessException(409, "任务冻结P币不足，无法发奖");
            }
            task.setEscrowRemaining(safe(task.getEscrowRemaining()) - pReward);
        }
        int pBefore = safe(winner.getDeposit());
        if (pReward > 0) {
            winner.setDeposit(checkedAdd(pBefore, pReward, "P币奖励"));
            addDepositLedger(winner,
                    Integer.valueOf(1).equals(task.getOfficial()) ? "official_task_reward" : "task_reward",
                    pReward, "完成任务：" + task.getTitle(), pBefore, winner.getDeposit(),
                    "event_task", "event_task_claims", claim.getId(), playerName(adminId));
        }

        int bountyReward = safe(claim.getBountyRewardSnapshot());
        int bountyBefore = safe(winner.getBounty());
        if (bountyReward > 0) {
            winner.setBounty(checkedAdd(bountyBefore, bountyReward, "赏金积分奖励"));
            PlayerBountyLedger bountyLedger = new PlayerBountyLedger();
            bountyLedger.setPlayerId(winner.getId());
            bountyLedger.setSeason(task.getSeason());
            bountyLedger.setTaskId(task.getId());
            bountyLedger.setClaimId(claim.getId());
            bountyLedger.setProofId(proof.getId());
            bountyLedger.setType("task_reward");
            bountyLedger.setAmount(bountyReward);
            bountyLedger.setReason("完成任务：" + task.getTitle());
            bountyLedger.setBalanceBefore(bountyBefore);
            bountyLedger.setBalanceAfter(winner.getBounty());
            bountyLedger.setOperator(playerName(adminId));
            bountyLedgerMapper.insert(bountyLedger);
        }
        playerMapper.updateById(winner);

        LocalDateTime now = now();
        proof.setStatus(PROOF_STATUS_APPROVED);
        proof.setReviewComment("审核通过");
        proof.setReviewedByPlayerId(adminId);
        proof.setReviewedAt(now);
        proofMapper.updateById(proof);
        claim.setStatus(COMPLETED);
        claim.setCompletedAt(now);
        claimMapper.updateById(claim);
        task.setCompletedCount(safe(task.getCompletedCount()) + 1);
        taskMapper.updateById(task);
        return toClaimVO(claim, true);
    }

    @Transactional
    public EventTaskDtos.ClaimVO revokeCompletion(
            Long adminId,
            Long claimId,
            EventTaskDtos.CompletionRevokeRequest request) {
        String reason = requireComment(request == null ? null : request.getReason());
        EventTaskClaim snapshot = claimMapper.selectById(claimId);
        if (snapshot == null) {
            throw new BusinessException(404, "任务完成记录不存在");
        }

        EventTask task = taskMapper.selectByIdForUpdate(snapshot.getTaskId());
        EventTaskClaim claim = claimMapper.selectByIdForUpdate(claimId);
        if (claim == null || !COMPLETED.equals(claim.getStatus())) {
            throw new BusinessException(409, "只有尚未撤回的完成记录可以撤回");
        }
        if (task == null || !TASK_PUBLISHED.equals(task.getStatus())) {
            throw new BusinessException(409, "任务已结束，无法撤回完成并返还接取名额");
        }

        EventTaskProof proof = proofMapper.selectApprovedByClaimForUpdate(claimId);
        if (proof == null) {
            throw new BusinessException(409, "未找到对应的已通过证明，无法自动撤回");
        }
        Player winner = playerMapper.selectByIdForUpdate(claim.getPlayerId());
        if (winner == null) {
            throw new BusinessException(404, "完成任务的选手不存在");
        }

        int pReward = safe(claim.getPRewardSnapshot());
        int pBefore = safe(winner.getDeposit());
        if (pReward > 0) {
            winner.setDeposit(checkedAdd(pBefore, -pReward, "P币奖励回退"));
            addDepositLedger(winner,
                    Integer.valueOf(1).equals(task.getOfficial())
                            ? "official_task_reward_reversal"
                            : "task_reward_reversal",
                    -pReward, "撤回任务完成奖励：" + task.getTitle() + "；" + reason,
                    pBefore, winner.getDeposit(),
                    "event_task", "event_task_claims", claim.getId(), playerName(adminId));
        }
        if (!Integer.valueOf(1).equals(task.getOfficial())) {
            int currentReward = safe(task.getPReward());
            int restoredEscrow = checkedAdd(safe(task.getEscrowRemaining()), currentReward, "任务冻结P币");
            int newTotal = checkedAdd(safe(task.getEscrowTotal()), currentReward - pReward, "任务冻结P币总额");
            if (restoredEscrow > newTotal || newTotal < 0) {
                throw new BusinessException(409, "任务冻结P币记录异常，无法自动撤回");
            }
            int adjustment = currentReward - pReward;
            if (adjustment != 0) {
                Player publisher = playerMapper.selectByIdForUpdate(task.getPublisherPlayerId());
                if (publisher == null) {
                    throw new BusinessException(404, "任务发布者不存在，无法调整冻结P币");
                }
                int publisherBefore = safe(publisher.getDeposit());
                if (adjustment > 0 && publisherBefore < adjustment) {
                    throw new BusinessException(400, "发布者个人P币不足，撤回完成需追加冻结 " + adjustment + "P");
                }
                int publisherAfter = checkedAdd(publisherBefore, -adjustment, "发布者个人P币");
                publisher.setDeposit(publisherAfter);
                playerMapper.updateById(publisher);
                addDepositLedger(publisher,
                        adjustment > 0 ? "task_reward_revoke_extra_escrow" : "task_reward_revoke_escrow_refund",
                        -adjustment, "撤回完成后按当前奖励调整冻结：" + task.getTitle(),
                        publisherBefore, publisherAfter, "event_task", "event_task_claims", claimId, playerName(adminId));
            }
            task.setEscrowRemaining(restoredEscrow);
            task.setEscrowTotal(newTotal);
        }

        int bountyReward = safe(claim.getBountyRewardSnapshot());
        int bountyBefore = safe(winner.getBounty());
        if (bountyReward > 0) {
            winner.setBounty(checkedAdd(bountyBefore, -bountyReward, "赏金积分奖励回退"));
            PlayerBountyLedger bountyLedger = new PlayerBountyLedger();
            bountyLedger.setPlayerId(winner.getId());
            bountyLedger.setSeason(task.getSeason());
            bountyLedger.setTaskId(task.getId());
            bountyLedger.setClaimId(claim.getId());
            bountyLedger.setProofId(proof.getId());
            bountyLedger.setType("task_reward_reversal");
            bountyLedger.setAmount(-bountyReward);
            bountyLedger.setReason("撤回任务完成奖励：" + task.getTitle() + "；" + reason);
            bountyLedger.setBalanceBefore(bountyBefore);
            bountyLedger.setBalanceAfter(winner.getBounty());
            bountyLedger.setOperator(playerName(adminId));
            bountyLedgerMapper.insert(bountyLedger);
        }
        playerMapper.updateById(winner);

        int claimedCount = safe(task.getClaimedCount());
        int completedCount = safe(task.getCompletedCount());
        if (claimedCount <= 0 || completedCount <= 0) {
            throw new BusinessException(409, "任务人数统计异常，无法自动撤回");
        }
        LocalDateTime now = now();
        task.setClaimedCount(claimedCount - 1);
        task.setCompletedCount(completedCount - 1);
        taskMapper.updateById(task);

        proof.setStatus(PROOF_STATUS_REVOKED);
        proof.setReviewComment(reason);
        proof.setReviewedByPlayerId(adminId);
        proof.setReviewedAt(now);
        proofMapper.updateById(proof);

        claim.setStatus(COMPLETION_REVOKED);
        claim.setTerminatedAt(now);
        claimMapper.updateById(claim);
        addReview(task.getId(), "REVOKE_COMPLETION", adminId,
                "撤回完成记录 #" + claim.getId() + "：" + reason,
                task.getClaimFee(), task.getMaxClaimants());
        return toClaimVO(claim, true);
    }

    @Transactional
    public void closeTask(Long adminId, Long taskId, EventTaskDtos.CloseTaskRequest request) {
        String reason = requireComment(request == null ? null : request.getReason());
        EventTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || !TASK_PUBLISHED.equals(task.getStatus())) {
            throw new BusinessException(409, "只有进行中的任务可以注销");
        }
        LocalDateTime now = now();
        List<EventTaskClaim> unfinished = claimMapper.selectList(new LambdaQueryWrapper<EventTaskClaim>()
                .eq(EventTaskClaim::getTaskId, taskId)
                .in(EventTaskClaim::getStatus, List.of(CLAIMED, PROOF_PENDING, PROOF_RETURNED))
                .eq(EventTaskClaim::getDeleted, 0));
        for (EventTaskClaim claim : unfinished) {
            EventTaskClaim locked = claimMapper.selectByIdForUpdate(claim.getId());
            if (locked != null && List.of(CLAIMED, PROOF_PENDING, PROOF_RETURNED).contains(locked.getStatus())) {
                locked.setStatus(TERMINATED);
                locked.setTerminatedAt(now);
                claimMapper.updateById(locked);
                voidPendingProofs(locked.getId(), "任务已注销");
            }
        }

        int refund = safe(task.getEscrowRemaining());
        if (!Integer.valueOf(1).equals(task.getOfficial()) && refund > 0) {
            Player publisher = playerMapper.selectByIdForUpdate(task.getPublisherPlayerId());
            if (publisher == null) {
                throw new BusinessException(404, "任务发布者不存在，无法退还剩余悬赏");
            }
            int before = safe(publisher.getDeposit());
            publisher.setDeposit(checkedAdd(before, refund, "任务退款"));
            playerMapper.updateById(publisher);
            addDepositLedger(publisher, "task_reward_escrow_refund", refund,
                    "任务注销，退还剩余悬赏：" + task.getTitle(), before, publisher.getDeposit(),
                    "event_task", "event_tasks", task.getId(), playerName(adminId));
            task.setEscrowRemaining(0);
        }
        task.setStatus(TASK_CLOSED);
        task.setClosedAt(now);
        task.setCloseReason(reason);
        taskMapper.updateById(task);
        addReview(taskId, "CLOSE", adminId, reason, task.getClaimFee(), task.getMaxClaimants());
    }

    private void validateTaskWrite(EventTaskDtos.TaskWriteRequest request) {
        if (request == null) {
            throw new BusinessException(400, "任务内容不能为空");
        }
        String title = trimToNull(request.getTitle());
        String requirements = trimToNull(request.getRequirements());
        if (title == null || title.length() > 200) {
            throw new BusinessException(400, "任务标题不能为空且不能超过200字");
        }
        if (requirements == null || requirements.length() > 10_000) {
            throw new BusinessException(400, "任务要求不能为空且不能超过10000字");
        }
        int p = request.getPReward() == null ? -1 : request.getPReward();
        int bounty = request.getBountyReward() == null ? -1 : request.getBountyReward();
        if (p < 0 || p > MAX_REWARD || bounty < 0 || bounty > MAX_REWARD || p + bounty <= 0) {
            throw new BusinessException(400, "P币和赏金积分必须为非负数，且至少一项大于0");
        }
        if (request.getBudgetNote() != null && request.getBudgetNote().trim().length() > 500) {
            throw new BusinessException(400, "备注不能超过500字");
        }
    }

    private void validateAdminConfig(Integer claimFee, Integer maxClaimants) {
        if (claimFee == null || claimFee < 0 || claimFee > MAX_REWARD) {
            throw new BusinessException(400, "接取费用必须为0到10000000之间的整数");
        }
        if (maxClaimants == null || maxClaimants < 1 || maxClaimants > MAX_CLAIMANTS) {
            throw new BusinessException(400, "最大接取人数必须为1到100");
        }
    }

    private void validateProof(String description, List<MultipartFile> files) {
        if (description != null && description.trim().length() > 5_000) {
            throw new BusinessException(400, "证明说明不能超过5000字");
        }
        if (files == null || files.isEmpty() || files.size() > 5) {
            throw new BusinessException(400, "请上传1至5张证明截图");
        }
        long total = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new BusinessException(400, "证明截图不能为空");
            }
            if (file.getSize() > MAX_IMAGE_BYTES) {
                throw new BusinessException(400, "单张截图不能超过10MB");
            }
            total += file.getSize();
            detectImageExtension(file);
        }
        if (total > 20L * 1024 * 1024) {
            throw new BusinessException(400, "单次上传总大小不能超过20MB");
        }
    }

    private String detectImageExtension(MultipartFile file) {
        byte[] header = new byte[12];
        try (InputStream input = file.getInputStream()) {
            int read = input.read(header);
            if (read >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff) {
                return "jpg";
            }
            if (read >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
                return "png";
            }
            if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                return "webp";
            }
        } catch (IOException e) {
            throw new BusinessException(400, "无法读取证明截图");
        }
        throw new BusinessException(400, "证明截图只支持 JPG、PNG 或 WebP");
    }

    private void applyWrite(EventTask task, EventTaskDtos.TaskWriteRequest request) {
        task.setTitle(request.getTitle().trim());
        task.setRequirements(request.getRequirements().trim());
        task.setPReward(request.getPReward());
        task.setBountyReward(request.getBountyReward());
        task.setBudgetNote(trimToNull(request.getBudgetNote()));
        task.setAnonymous(Boolean.TRUE.equals(request.getAnonymous()) ? 1 : 0);
    }

    private EventTaskReview addReview(Long taskId, String action, Long operatorId, String comment, Integer fee, Integer max) {
        EventTaskReview review = new EventTaskReview();
        review.setTaskId(taskId);
        review.setAction(action);
        review.setOperatorPlayerId(operatorId);
        review.setComment(comment);
        review.setClaimFeeSnapshot(fee);
        review.setMaxClaimantsSnapshot(max);
        reviewMapper.insert(review);
        return review;
    }

    private String taskSnapshot(EventTask task) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", task.getTitle());
        snapshot.put("requirements", task.getRequirements());
        snapshot.put("budgetNote", task.getBudgetNote());
        snapshot.put("pReward", task.getPReward());
        snapshot.put("bountyReward", task.getBountyReward());
        try {
            return SNAPSHOT_MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(500, "无法记录任务编辑历史");
        }
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

    private void voidPendingProofs(Long claimId, String reason) {
        List<EventTaskProof> proofs = proofMapper.selectList(new LambdaQueryWrapper<EventTaskProof>()
                .eq(EventTaskProof::getClaimId, claimId)
                .eq(EventTaskProof::getStatus, PROOF_STATUS_PENDING)
                .eq(EventTaskProof::getDeleted, 0));
        for (EventTaskProof proof : proofs) {
            proof.setStatus(PROOF_STATUS_VOIDED);
            proof.setReviewComment(reason);
            proof.setReviewedAt(now());
            proofMapper.updateById(proof);
        }
    }

    private int nextAttempt(Long claimId) {
        EventTaskProof last = proofMapper.selectList(new LambdaQueryWrapper<EventTaskProof>()
                        .eq(EventTaskProof::getClaimId, claimId)
                        .eq(EventTaskProof::getDeleted, 0)
                        .orderByDesc(EventTaskProof::getAttemptNo)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        return last == null ? 1 : last.getAttemptNo() + 1;
    }

    private List<EventTaskDtos.TaskVO> toTaskVOs(List<EventTask> tasks, Player viewer, boolean includeClaims) {
        return tasks.stream().map(task -> toTaskVO(task, viewer, includeClaims)).collect(Collectors.toList());
    }

    private EventTaskDtos.TaskVO toTaskVO(EventTask task, Player viewer, boolean includeClaims) {
        EventTaskDtos.TaskVO vo = new EventTaskDtos.TaskVO();
        boolean owner = viewer != null && Objects.equals(viewer.getId(), task.getPublisherPlayerId());
        boolean anonymous = Integer.valueOf(1).equals(task.getAnonymous());
        boolean maySeePublisher = owner || includeClaims || !anonymous;
        vo.setId(task.getId());
        vo.setSeason(task.getSeason());
        vo.setPublisherPlayerId(maySeePublisher ? task.getPublisherPlayerId() : null);
        vo.setPublisherName(maySeePublisher ? task.getPublisherNameSnapshot() : "匿名发布者");
        vo.setOfficial(Integer.valueOf(1).equals(task.getOfficial()));
        vo.setAnonymous(anonymous);
        vo.setTitle(task.getTitle());
        vo.setRequirements(task.getRequirements());
        vo.setPReward(safe(task.getPReward()));
        vo.setBountyReward(safe(task.getBountyReward()));
        vo.setBudgetNote(owner || includeClaims ? task.getBudgetNote() : null);
        vo.setAnonymousFeeRateSnapshot(owner || includeClaims ? task.getAnonymousFeeRateSnapshot() : null);
        vo.setAnonymousFeeAmount(owner || includeClaims ? safe(task.getAnonymousFeeAmount()) : null);
        vo.setClaimFee(task.getClaimFee());
        vo.setMaxClaimants(task.getMaxClaimants());
        vo.setClaimedCount(safe(task.getClaimedCount()));
        vo.setCompletedCount(safe(task.getCompletedCount()));
        vo.setRemainingSlots(task.getMaxClaimants() == null ? null : Math.max(0, task.getMaxClaimants() - safe(task.getClaimedCount())));
        vo.setEscrowTotal(safe(task.getEscrowTotal()));
        vo.setEscrowRemaining(safe(task.getEscrowRemaining()));
        vo.setStatus(task.getStatus());
        vo.setLatestReviewComment(task.getLatestReviewComment());
        vo.setReviewedAt(task.getReviewedAt());
        vo.setPublishedAt(task.getPublishedAt());
        vo.setClosedAt(task.getClosedAt());
        vo.setCloseReason(task.getCloseReason());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setOwnedByViewer(owner);
        EventTaskClaim viewerClaim = viewer == null ? null : findClaim(task.getId(), viewer.getId());
        vo.setViewerClaimStatus(viewerClaim == null ? null : viewerClaim.getStatus());
        vo.setViewerClaimId(viewerClaim == null ? null : viewerClaim.getId());
        LocalDateTime reclaimAt = viewerClaim != null && ADMIN_CANCELLED.equals(viewerClaim.getStatus())
                && viewerClaim.getTerminatedAt() != null ? viewerClaim.getTerminatedAt().plusHours(1) : null;
        vo.setViewerReclaimAvailableAt(reclaimAt);
        vo.setCanClaim(viewer != null && TASK_PUBLISHED.equals(task.getStatus()) && !owner
                && (viewerClaim == null || reclaimAt != null && !now().isBefore(reclaimAt))
                && vo.getRemainingSlots() != null && vo.getRemainingSlots() > 0);
        if (includeClaims) {
            vo.setClaims(claimMapper.selectList(new LambdaQueryWrapper<EventTaskClaim>()
                            .eq(EventTaskClaim::getTaskId, task.getId())
                            .eq(EventTaskClaim::getDeleted, 0)
                            .orderByDesc(EventTaskClaim::getClaimedAt))
                    .stream().map(claim -> toClaimVO(claim, true)).collect(Collectors.toList()));
        }
        return vo;
    }

    private EventTaskDtos.ClaimVO toClaimVO(EventTaskClaim claim, boolean includeProofs) {
        EventTaskDtos.ClaimVO vo = new EventTaskDtos.ClaimVO();
        vo.setId(claim.getId());
        vo.setTaskId(claim.getTaskId());
        vo.setPlayerId(claim.getPlayerId());
        Player player = playerMapper.selectById(claim.getPlayerId());
        vo.setPlayerName(player == null ? "" : player.getName());
        EventTask task = taskMapper.selectById(claim.getTaskId());
        vo.setTaskTitle(claim.getTitleSnapshot() != null ? claim.getTitleSnapshot() : task == null ? "" : task.getTitle());
        vo.setTaskRequirements(claim.getRequirementsSnapshot() != null ? claim.getRequirementsSnapshot()
                : task == null ? "" : task.getRequirements());
        vo.setTaskSeason(task == null ? "" : task.getSeason());
        vo.setStatus(claim.getStatus());
        vo.setFeeAmount(safe(claim.getFeeAmount()));
        vo.setPReward(safe(claim.getPRewardSnapshot()));
        vo.setBountyReward(safe(claim.getBountyRewardSnapshot()));
        vo.setClaimedAt(claim.getClaimedAt());
        vo.setProofSubmittedAt(claim.getProofSubmittedAt());
        vo.setAbandonedAt(claim.getAbandonedAt());
        vo.setAbandonRefunded(Integer.valueOf(1).equals(claim.getAbandonRefunded()));
        vo.setCompletedAt(claim.getCompletedAt());
        vo.setTerminatedAt(claim.getTerminatedAt());
        vo.setAdminCancelReason(claim.getAdminCancelReason());
        if (ADMIN_CANCELLED.equals(claim.getStatus()) && claim.getTerminatedAt() != null) {
            vo.setReclaimAvailableAt(claim.getTerminatedAt().plusHours(1));
        }
        if (COMPLETION_REVOKED.equals(claim.getStatus())) {
            vo.setCompletionRevokedAt(claim.getTerminatedAt());
            EventTaskProof revokedProof = proofMapper.selectList(new LambdaQueryWrapper<EventTaskProof>()
                            .eq(EventTaskProof::getClaimId, claim.getId())
                            .eq(EventTaskProof::getStatus, PROOF_STATUS_REVOKED)
                            .eq(EventTaskProof::getDeleted, 0)
                            .orderByDesc(EventTaskProof::getReviewedAt)
                            .last("LIMIT 1"))
                    .stream().findFirst().orElse(null);
            if (revokedProof != null) {
                vo.setCompletionRevokeReason(revokedProof.getReviewComment());
                vo.setCompletionRevokedByName(playerName(revokedProof.getReviewedByPlayerId()));
            }
        }
        LocalDateTime freeUntil = claim.getClaimedAt() == null ? null : claim.getClaimedAt().plusMinutes(30);
        vo.setFreeAbandonUntil(freeUntil);
        vo.setFreeAbandonAvailable(freeUntil != null && !now().isAfter(freeUntil)
                && List.of(CLAIMED, PROOF_PENDING, PROOF_RETURNED).contains(claim.getStatus())
                && task != null && TASK_PUBLISHED.equals(task.getStatus()));
        if (includeProofs) {
            vo.setProofs(proofMapper.selectList(new LambdaQueryWrapper<EventTaskProof>()
                            .eq(EventTaskProof::getClaimId, claim.getId())
                            .eq(EventTaskProof::getDeleted, 0)
                            .orderByDesc(EventTaskProof::getAttemptNo))
                    .stream().map(this::toProofVO).collect(Collectors.toList()));
        }
        return vo;
    }

    private EventTaskDtos.ProofVO toProofVO(EventTaskProof proof) {
        EventTaskDtos.ProofVO vo = new EventTaskDtos.ProofVO();
        vo.setId(proof.getId());
        vo.setTaskId(proof.getTaskId());
        vo.setClaimId(proof.getClaimId());
        vo.setPlayerId(proof.getPlayerId());
        Player player = playerMapper.selectById(proof.getPlayerId());
        vo.setPlayerName(player == null ? "" : player.getName());
        vo.setAttemptNo(proof.getAttemptNo());
        vo.setDescription(proof.getDescription());
        vo.setStatus(proof.getStatus());
        vo.setReviewComment(proof.getReviewComment());
        vo.setReviewedAt(proof.getReviewedAt());
        vo.setCreatedAt(proof.getCreatedAt());
        vo.setImages(proofImageMapper.selectList(new LambdaQueryWrapper<EventTaskProofImage>()
                        .eq(EventTaskProofImage::getProofId, proof.getId())
                        .eq(EventTaskProofImage::getDeleted, 0)
                        .orderByAsc(EventTaskProofImage::getId))
                .stream().map(image -> {
                    EventTaskDtos.ImageVO imageVO = new EventTaskDtos.ImageVO();
                    imageVO.setId(image.getId());
                    imageVO.setLabel(image.getLabel());
                    imageVO.setUrl(image.getUrl());
                    return imageVO;
                }).collect(Collectors.toList()));
        return vo;
    }

    private EventTaskClaim findClaim(Long taskId, Long playerId) {
        return claimMapper.selectOne(new LambdaQueryWrapper<EventTaskClaim>()
                .eq(EventTaskClaim::getTaskId, taskId)
                .eq(EventTaskClaim::getPlayerId, playerId)
                .eq(EventTaskClaim::getDeleted, 0)
                .orderByDesc(EventTaskClaim::getId)
                .last("LIMIT 1"));
    }

    private EventTask requireTask(Long id) {
        EventTask task = id == null ? null : taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        return task;
    }

    private Player requirePlayer(Long id) {
        Player player = id == null ? null : playerMapper.selectById(id);
        if (player == null || Integer.valueOf(1).equals(player.getDeleted())) {
            throw new BusinessException(404, "选手不存在");
        }
        return player;
    }

    private void requireOwner(EventTask task, Long playerId) {
        if (task == null || !Objects.equals(task.getPublisherPlayerId(), playerId)) {
            throw new BusinessException(404, "任务不存在或无权操作");
        }
    }

    private String playerName(Long playerId) {
        Player player = playerMapper.selectById(playerId);
        return player == null ? "admin" : player.getName();
    }

    private String requireComment(String value) {
        String comment = trimToNull(value);
        if (comment == null || comment.length() > 500) {
            throw new BusinessException(400, "请填写不超过500字的原因");
        }
        return comment;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private static int checkedAdd(int left, int right, String fieldName) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            throw new BusinessException(400, fieldName + "数值过大");
        }
    }

    private int anonymousFeeRate() {
        int rate = ruleParameterService.getInt(ANONYMOUS_FEE_RATE_KEY);
        if (rate < 0 || rate > 100) {
            throw new BusinessException(409, "匿名发布费率配置异常，请联系管理员");
        }
        return rate;
    }

    private static int calculateAnonymousFee(int escrow, int rate) {
        long percentageFee = ((long) escrow * rate + 99L) / 100L;
        return (int) Math.max(ANONYMOUS_MINIMUM_FEE, percentageFee);
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "/uploads";
        }
        return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }
}
