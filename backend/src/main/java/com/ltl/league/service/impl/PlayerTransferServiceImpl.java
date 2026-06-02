package com.ltl.league.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.dto.PlayerTransferPreviewVO;
import com.ltl.league.dto.PlayerTransferRequest;
import com.ltl.league.dto.PlayerTransferVO;
import com.ltl.league.entity.PLedger;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.PlayerDepositLedger;
import com.ltl.league.entity.PlayerTransfer;
import com.ltl.league.entity.Team;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.PLedgerMapper;
import com.ltl.league.mapper.PlayerDepositLedgerMapper;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.mapper.PlayerTransferMapper;
import com.ltl.league.mapper.TeamMapper;
import com.ltl.league.service.PlayerTransferService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlayerTransferServiceImpl implements PlayerTransferService {

    private static final String RECIPIENT_PLAYER = "PLAYER";
    private static final String RECIPIENT_TEAM = "TEAM";
    private static final String STATUS_COMPLETED = "completed";
    private static final int MIN_AMOUNT = 10;
    private static final int MAX_AMOUNT = 100000;
    private static final int PERSONAL_TRANSFER_COOLDOWN_DAYS = 3;

    private final PlayerMapper playerMapper;
    private final TeamMapper teamMapper;
    private final PlayerTransferMapper transferMapper;
    private final PlayerDepositLedgerMapper playerDepositLedgerMapper;
    private final PLedgerMapper pLedgerMapper;

    public PlayerTransferServiceImpl(
            PlayerMapper playerMapper,
            TeamMapper teamMapper,
            PlayerTransferMapper transferMapper,
            PlayerDepositLedgerMapper playerDepositLedgerMapper,
            PLedgerMapper pLedgerMapper) {
        this.playerMapper = playerMapper;
        this.teamMapper = teamMapper;
        this.transferMapper = transferMapper;
        this.playerDepositLedgerMapper = playerDepositLedgerMapper;
        this.pLedgerMapper = pLedgerMapper;
    }

    @Override
    public PlayerTransferPreviewVO preview(Long donorPlayerId, PlayerTransferRequest request) {
        Player donor = requirePlayer(donorPlayerId, "赠与人不存在");
        validateActivePlayer(donor, "赠与人状态异常");
        validateAmount(request);

        String recipientType = normalizeRecipientType(request);
        Integer amount = request.getAmount();
        PlayerTransferPreviewVO vo = basePreview(donor, recipientType, amount);

        try {
            if (RECIPIENT_PLAYER.equals(recipientType)) {
                Player recipient = requirePlayer(request.getRecipientPlayerId(), "受赠人不存在");
                validateActivePlayer(recipient, "受赠人状态异常");
                if (Objects.equals(donor.getId(), recipient.getId())) {
                    throw new BusinessException(400, "不能转赠给自己");
                }
                fillRecipientPlayer(vo, recipient);
                applyCooldownPreview(vo, donor.getId());
            } else {
                if (donor.getTeamId() == null) {
                    throw new BusinessException(400, "自由人不能赠与战队");
                }
                Team team = requireTeam(donor.getTeamId(), "赠与战队不存在");
                fillRecipientTeam(vo, team);
            }

            Integer fee = calculateFee(recipientType, amount);
            Integer totalCost = amount + fee;
            vo.setFeeAmount(fee);
            vo.setTotalCost(totalCost);
            vo.setBalanceAfter((donor.getDeposit() != null ? donor.getDeposit() : 0) - totalCost);
            if (Boolean.FALSE.equals(vo.getAllowed())) {
                return vo;
            }
            if (vo.getBalanceAfter() < 0) {
                vo.setAllowed(false);
                vo.setMessage("积分不足");
                return vo;
            }
            vo.setAllowed(true);
            vo.setMessage("可以转赠");
            return vo;
        } catch (BusinessException e) {
            vo.setAllowed(false);
            vo.setMessage(e.getMessage());
            return vo;
        }
    }

    @Override
    @Transactional
    public PlayerTransferVO transfer(Long donorPlayerId, PlayerTransferRequest request) {
        validateAmount(request);
        String recipientType = normalizeRecipientType(request);

        if (RECIPIENT_PLAYER.equals(recipientType)) {
            return transferToPlayer(donorPlayerId, request.getRecipientPlayerId(), request.getAmount());
        }
        return transferToOwnTeam(donorPlayerId, request.getAmount());
    }

    @Override
    public List<PlayerTransferVO> listMine(Long donorPlayerId, Integer limit) {
        List<PlayerTransfer> transfers = transferMapper.selectList(new LambdaQueryWrapper<PlayerTransfer>()
                .eq(PlayerTransfer::getDonorPlayerId, donorPlayerId)
                .eq(PlayerTransfer::getDeleted, 0)
                .orderByDesc(PlayerTransfer::getCreatedAt)
                .last("LIMIT " + normalizeLimit(limit)));
        if (transfers.isEmpty()) {
            return Collections.emptyList();
        }
        return toVOs(transfers);
    }

    private PlayerTransferVO transferToPlayer(Long donorPlayerId, Long recipientPlayerId, Integer amount) {
        if (recipientPlayerId == null) {
            throw new BusinessException(400, "请选择受赠选手");
        }
        if (Objects.equals(donorPlayerId, recipientPlayerId)) {
            throw new BusinessException(400, "不能转赠给自己");
        }

        List<Long> lockIds = new ArrayList<>();
        lockIds.add(donorPlayerId);
        lockIds.add(recipientPlayerId);
        lockIds = lockIds.stream().filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
        Map<Long, Player> lockedPlayers = playerMapper.selectByIdsForUpdate(lockIds).stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));

        Player donor = lockedPlayers.get(donorPlayerId);
        Player recipient = lockedPlayers.get(recipientPlayerId);
        validateTransferPlayers(donor, recipient);
        ensurePersonalTransferCooldown(donor.getId());

        Integer fee = calculateFee(RECIPIENT_PLAYER, amount);
        Integer totalCost = amount + fee;
        Integer donorBalance = donor.getDeposit() != null ? donor.getDeposit() : 0;
        if (donorBalance < totalCost) {
            throw new BusinessException(400, "积分不足");
        }

        Integer recipientBalance = recipient.getDeposit() != null ? recipient.getDeposit() : 0;
        donor.setDeposit(donorBalance - totalCost);
        recipient.setDeposit(recipientBalance + amount);
        playerMapper.updateById(donor);
        playerMapper.updateById(recipient);

        PlayerTransfer transfer = insertTransfer(donor.getId(), RECIPIENT_PLAYER, recipient.getId(), null, amount, fee, totalCost);
        insertPlayerLedger(donor.getId(), "transfer_out", -totalCost,
                "转赠给选手 " + recipient.getName() + "，到账 " + amount + "P，手续费 " + fee + "P（销毁）",
                donorBalance, donor.getDeposit());
        insertPlayerLedger(recipient.getId(), "transfer_in", amount,
                "收到选手 " + donor.getName() + " 转赠",
                recipientBalance, recipient.getDeposit());

        return toVO(transfer, donor, recipient, null);
    }

    private PlayerTransferVO transferToOwnTeam(Long donorPlayerId, Integer amount) {
        Player donor = playerMapper.selectByIdForUpdate(donorPlayerId);
        if (donor == null) {
            throw new BusinessException(404, "赠与人不存在");
        }
        validateActivePlayer(donor, "赠与人状态异常");
        if (donor.getTeamId() == null) {
            throw new BusinessException(400, "自由人不能赠与战队");
        }

        Team team = teamMapper.selectByIdForUpdate(donor.getTeamId());
        if (team == null) {
            throw new BusinessException(404, "赠与战队不存在");
        }

        Integer fee = calculateFee(RECIPIENT_TEAM, amount);
        Integer totalCost = amount + fee;
        Integer donorBalance = donor.getDeposit() != null ? donor.getDeposit() : 0;
        if (donorBalance < totalCost) {
            throw new BusinessException(400, "积分不足");
        }

        Integer teamBalance = team.getPCoins() != null ? team.getPCoins() : 0;
        donor.setDeposit(donorBalance - totalCost);
        team.setPCoins(teamBalance + amount);
        playerMapper.updateById(donor);
        teamMapper.updateById(team);

        PlayerTransfer transfer = insertTransfer(donor.getId(), RECIPIENT_TEAM, null, team.getId(), amount, fee, totalCost);
        insertPlayerLedger(donor.getId(), "transfer_out", -totalCost,
                "赠与战队 " + team.getState() + " " + team.getName() + "，到账 " + amount + "P，手续费 " + fee + "P（销毁）",
                donorBalance, donor.getDeposit());
        insertTeamLedger(team.getId(), amount,
                "选手 " + donor.getName() + " 赠与战队，手续费 " + fee + "P 由赠与人承担并销毁",
                teamBalance, team.getPCoins());

        return toVO(transfer, donor, null, team);
    }

    private void validateTransferPlayers(Player donor, Player recipient) {
        if (donor == null) {
            throw new BusinessException(404, "赠与人不存在");
        }
        if (recipient == null) {
            throw new BusinessException(404, "受赠人不存在");
        }
        validateActivePlayer(donor, "赠与人状态异常");
        validateActivePlayer(recipient, "受赠人状态异常");
        if (Objects.equals(donor.getId(), recipient.getId())) {
            throw new BusinessException(400, "不能转赠给自己");
        }
    }

    private void validateActivePlayer(Player player, String message) {
        if (player == null || player.getDeleted() != null && player.getDeleted() == 1) {
            throw new BusinessException(404, message);
        }
        Integer status = player.getStatus();
        if (status == null || status < 1 || status > 3) {
            throw new BusinessException(400, message);
        }
    }

    private void validateAmount(PlayerTransferRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求参数不能为空");
        }
        Integer amount = request.getAmount();
        if (amount == null) {
            throw new BusinessException(400, "请输入转赠金额");
        }
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT || amount % 10 != 0) {
            throw new BusinessException(400, "转赠金额必须为 10 到 100000 之间的 10 的倍数");
        }
    }

    private String normalizeRecipientType(PlayerTransferRequest request) {
        String recipientType = request.getRecipientType();
        if (recipientType == null || recipientType.isBlank()) {
            throw new BusinessException(400, "请选择转赠类型");
        }
        recipientType = recipientType.trim().toUpperCase();
        if (!RECIPIENT_PLAYER.equals(recipientType) && !RECIPIENT_TEAM.equals(recipientType)) {
            throw new BusinessException(400, "转赠类型不正确");
        }
        return recipientType;
    }

    private Integer calculateFee(String recipientType, Integer amount) {
        if (RECIPIENT_TEAM.equals(recipientType)) {
            return amount / 10;
        }
        int firstTier = Math.min(amount, 1000);
        int secondTier = Math.min(Math.max(amount - 1000, 0), 1000);
        int thirdTier = Math.max(amount - 2000, 0);
        return 100 + firstTier * 20 / 100 + secondTier * 40 / 100 + thirdTier * 60 / 100;
    }

    private void ensurePersonalTransferCooldown(Long donorPlayerId) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(PERSONAL_TRANSFER_COOLDOWN_DAYS);
        Long count = transferMapper.selectCount(new LambdaQueryWrapper<PlayerTransfer>()
                .eq(PlayerTransfer::getDonorPlayerId, donorPlayerId)
                .eq(PlayerTransfer::getRecipientType, RECIPIENT_PLAYER)
                .eq(PlayerTransfer::getStatus, STATUS_COMPLETED)
                .eq(PlayerTransfer::getDeleted, 0)
                .ge(PlayerTransfer::getCreatedAt, cutoff));
        if (count != null && count > 0) {
            throw new BusinessException(400, "三天内只能进行一次对个人的转赠");
        }
    }

    private void applyCooldownPreview(PlayerTransferPreviewVO vo, Long donorPlayerId) {
        PlayerTransfer lastTransfer = transferMapper.selectList(new LambdaQueryWrapper<PlayerTransfer>()
                .eq(PlayerTransfer::getDonorPlayerId, donorPlayerId)
                .eq(PlayerTransfer::getRecipientType, RECIPIENT_PLAYER)
                .eq(PlayerTransfer::getStatus, STATUS_COMPLETED)
                .eq(PlayerTransfer::getDeleted, 0)
                .orderByDesc(PlayerTransfer::getCreatedAt)
                .last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);
        if (lastTransfer == null || lastTransfer.getCreatedAt() == null) {
            return;
        }
        LocalDateTime nextAt = lastTransfer.getCreatedAt().plusDays(PERSONAL_TRANSFER_COOLDOWN_DAYS);
        if (nextAt.isAfter(LocalDateTime.now())) {
            vo.setAllowed(false);
            vo.setMessage("三天内只能进行一次对个人的转赠");
            vo.setNextPersonalTransferAt(nextAt);
        }
    }

    private Player requirePlayer(Long playerId, String message) {
        if (playerId == null) {
            throw new BusinessException(400, message);
        }
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BusinessException(404, message);
        }
        return player;
    }

    private Team requireTeam(Long teamId, String message) {
        if (teamId == null) {
            throw new BusinessException(400, message);
        }
        Team team = teamMapper.selectById(teamId);
        if (team == null || team.getDeleted() != null && team.getDeleted() == 1) {
            throw new BusinessException(404, message);
        }
        return team;
    }

    private PlayerTransferPreviewVO basePreview(Player donor, String recipientType, Integer amount) {
        PlayerTransferPreviewVO vo = new PlayerTransferPreviewVO();
        vo.setRecipientType(recipientType);
        vo.setAmount(amount);
        vo.setDonorBalance(donor.getDeposit() != null ? donor.getDeposit() : 0);
        vo.setAllowed(true);
        return vo;
    }

    private void fillRecipientPlayer(PlayerTransferPreviewVO vo, Player recipient) {
        vo.setRecipientPlayerId(recipient.getId());
        vo.setRecipientPlayerName(recipient.getName());
    }

    private void fillRecipientTeam(PlayerTransferPreviewVO vo, Team team) {
        vo.setRecipientTeamId(team.getId());
        vo.setRecipientTeamName(team.getName());
        vo.setRecipientTeamState(team.getState());
    }

    private PlayerTransfer insertTransfer(
            Long donorPlayerId,
            String recipientType,
            Long recipientPlayerId,
            Long recipientTeamId,
            Integer amount,
            Integer fee,
            Integer totalCost) {
        PlayerTransfer transfer = new PlayerTransfer();
        transfer.setDonorPlayerId(donorPlayerId);
        transfer.setRecipientType(recipientType);
        transfer.setRecipientPlayerId(recipientPlayerId);
        transfer.setRecipientTeamId(recipientTeamId);
        transfer.setAmount(amount);
        transfer.setFeeAmount(fee);
        transfer.setTotalCost(totalCost);
        transfer.setStatus(STATUS_COMPLETED);
        transferMapper.insert(transfer);
        return transfer;
    }

    private void insertPlayerLedger(Long playerId, String type, Integer amount, String reason, Integer before, Integer after) {
        PlayerDepositLedger ledger = new PlayerDepositLedger();
        ledger.setPlayerId(playerId);
        ledger.setType(type);
        ledger.setAmount(amount);
        ledger.setReason(reason);
        ledger.setBalanceBefore(before);
        ledger.setBalanceAfter(after);
        ledger.setSource("player_transfer");
        ledger.setOperator("system");
        ledger.setIsVoided(0);
        playerDepositLedgerMapper.insert(ledger);
    }

    private void insertTeamLedger(Long teamId, Integer amount, String reason, Integer before, Integer after) {
        PLedger ledger = new PLedger();
        ledger.setTeamId(teamId);
        ledger.setType("player_donation");
        ledger.setAmount(amount);
        ledger.setReason(reason);
        ledger.setSource("player_transfer");
        ledger.setBalanceBefore(before);
        ledger.setBalanceAfter(after);
        ledger.setIsVoided(0);
        pLedgerMapper.insert(ledger);
    }

    private List<PlayerTransferVO> toVOs(List<PlayerTransfer> transfers) {
        Set<Long> playerIds = transfers.stream()
                .flatMap(t -> {
                    List<Long> ids = new ArrayList<>();
                    ids.add(t.getDonorPlayerId());
                    ids.add(t.getRecipientPlayerId());
                    return ids.stream();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Player> players = playerIds.isEmpty() ? Collections.emptyMap() :
                playerMapper.selectBatchIds(playerIds).stream()
                        .collect(Collectors.toMap(Player::getId, Function.identity()));

        Set<Long> teamIds = transfers.stream()
                .map(PlayerTransfer::getRecipientTeamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Team> teams = teamIds.isEmpty() ? Collections.emptyMap() :
                teamMapper.selectBatchIds(teamIds).stream()
                        .collect(Collectors.toMap(Team::getId, Function.identity()));

        return transfers.stream()
                .map(t -> toVO(t, players.get(t.getDonorPlayerId()), players.get(t.getRecipientPlayerId()), teams.get(t.getRecipientTeamId())))
                .collect(Collectors.toList());
    }

    private PlayerTransferVO toVO(PlayerTransfer transfer, Player donor, Player recipient, Team team) {
        PlayerTransferVO vo = new PlayerTransferVO();
        vo.setId(transfer.getId());
        vo.setDonorPlayerId(transfer.getDonorPlayerId());
        vo.setDonorPlayerName(donor != null ? donor.getName() : "");
        vo.setRecipientType(transfer.getRecipientType());
        vo.setRecipientPlayerId(transfer.getRecipientPlayerId());
        vo.setRecipientPlayerName(recipient != null ? recipient.getName() : "");
        vo.setRecipientTeamId(transfer.getRecipientTeamId());
        vo.setRecipientTeamName(team != null ? team.getName() : "");
        vo.setRecipientTeamState(team != null ? team.getState() : "");
        vo.setAmount(transfer.getAmount());
        vo.setFeeAmount(transfer.getFeeAmount());
        vo.setTotalCost(transfer.getTotalCost());
        vo.setStatus(transfer.getStatus());
        vo.setCreatedAt(transfer.getCreatedAt());
        return vo;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        return Math.max(1, Math.min(limit, 200));
    }
}
