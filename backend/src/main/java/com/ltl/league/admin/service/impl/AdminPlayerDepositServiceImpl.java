package com.ltl.league.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.dto.*;
import com.ltl.league.admin.service.AdminPlayerDepositService;
import com.ltl.league.entity.*;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminPlayerDepositServiceImpl implements AdminPlayerDepositService {

    private final PlayerMapper playerMapper;
    private final PlayerDepositLedgerMapper depositLedgerMapper;
    private final TeamMapper teamMapper;

    public AdminPlayerDepositServiceImpl(
            PlayerMapper playerMapper,
            PlayerDepositLedgerMapper depositLedgerMapper,
            TeamMapper teamMapper) {
        this.playerMapper = playerMapper;
        this.depositLedgerMapper = depositLedgerMapper;
        this.teamMapper = teamMapper;
    }

    @Override
    @Transactional
    public void adjustPlayerDeposit(AdjustPlayerDepositRequest request) {
        if (request == null || request.getPlayerId() == null || request.getAmount() == null) {
            throw new BusinessException(400, "选手ID和金额不能为空");
        }
        if (request.getAmount() == 0) {
            throw new BusinessException(400, "调整金额不能为0");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(400, "请填写调整原因");
        }

        Player player = playerMapper.selectById(request.getPlayerId());
        if (player == null) {
            throw new BusinessException(404, "选手不存在");
        }

        Integer currentDeposit = player.getDeposit() != null ? player.getDeposit() : 0;
        Integer newDeposit = currentDeposit + request.getAmount();

        if (newDeposit < 0) {
            throw new BusinessException(400, "选手存款余额不足");
        }

        // 创建流水记录
        PlayerDepositLedger ledger = new PlayerDepositLedger();
        ledger.setPlayerId(player.getId());
        ledger.setMatchId(null);
        ledger.setResultId(null);
        ledger.setType("manual_adjustment");
        ledger.setAmount(request.getAmount());
        ledger.setReason(request.getReason().trim());
        ledger.setBalanceBefore(currentDeposit);
        ledger.setBalanceAfter(newDeposit);
        ledger.setSource("manual_admin");
        ledger.setOperator("admin");
        ledger.setIsVoided(0);
        depositLedgerMapper.insert(ledger);

        player.setDeposit(newDeposit);
        playerMapper.updateById(player);
    }

    @Override
    @Transactional
    public void addLoanFeeToPlayer(Long playerId, Integer amount) {
        if (playerId == null || amount == null || amount <= 0) {
            throw new BusinessException(400, "选手ID和金额不能为空且金额必须大于0");
        }

        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BusinessException(404, "选手不存在");
        }

        Integer currentDeposit = player.getDeposit() != null ? player.getDeposit() : 0;
        Integer newDeposit = currentDeposit + amount;

        // 创建流水记录
        PlayerDepositLedger ledger = new PlayerDepositLedger();
        ledger.setPlayerId(player.getId());
        ledger.setMatchId(null);
        ledger.setResultId(null);
        ledger.setType("loan_fee");
        ledger.setAmount(amount);
        ledger.setReason("租借费收入");
        ledger.setBalanceBefore(currentDeposit);
        ledger.setBalanceAfter(newDeposit);
        ledger.setSource("match_result");
        ledger.setOperator("system");
        ledger.setIsVoided(0);
        depositLedgerMapper.insert(ledger);

        player.setDeposit(newDeposit);
        playerMapper.updateById(player);
    }

    @Override
    @Transactional
    public Player createPlayer(CreatePlayerRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求参数不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException(400, "选手名称不能为空");
        }
        if (request.getValue() == null || request.getValue() < 0) {
            throw new BusinessException(400, "选手身价不能为空且不能小于0");
        }
        if (request.getStatus() == null) {
            request.setStatus(3); // 默认为自由人
        }

        // 如果是自由人（status=3），teamId必须为空
        if (request.getStatus() == 3 && request.getTeamId() != null) {
            throw new BusinessException(400, "自由人不能属于任何队伍");
        }
        // 如果不是自由人，teamId不能为空
        if (request.getStatus() != 3 && request.getTeamId() == null) {
            throw new BusinessException(400, "在职选手必须属于某个队伍");
        }

        Player player = new Player();
        player.setTeamId(request.getTeamId());
        player.setName(request.getName().trim());
        player.setValue(request.getValue());
        player.setPosition(request.getPosition());
        player.setGameAccount(request.getGameAccount());
        player.setPuuid(request.getPuuid());
        player.setIsSubstitute(request.getIsSubstitute() != null ? request.getIsSubstitute() : 0);
        player.setIsLoan(0);
        player.setLoanTeamId(null);
        player.setStatus(request.getStatus());
        player.setDeposit(0);

        playerMapper.insert(player);
        return player;
    }

    @Override
    @Transactional
    public Player updatePlayer(Long playerId, UpdatePlayerRequest request) {
        if (playerId == null) {
            throw new BusinessException(400, "选手ID不能为空");
        }

        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BusinessException(404, "选手不存在");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            player.setName(request.getName().trim());
        }
        if (request.getValue() != null && request.getValue() >= 0) {
            player.setValue(request.getValue());
        }
        if (request.getPosition() != null) {
            player.setPosition(request.getPosition());
        }
        if (request.getGameAccount() != null) {
            player.setGameAccount(request.getGameAccount());
        }
        if (request.getPuuid() != null) {
            player.setPuuid(request.getPuuid());
        }
        if (request.getIsSubstitute() != null) {
            player.setIsSubstitute(request.getIsSubstitute());
        }
        if (request.getStatus() != null) {
            // 状态变更时的逻辑验证
            if (request.getStatus() == 3 && request.getTeamId() != null) {
                throw new BusinessException(400, "自由人不能属于任何队伍");
            }
            if (request.getStatus() != 3 && request.getTeamId() == null) {
                throw new BusinessException(400, "在职选手必须属于某个队伍");
            }
            player.setStatus(request.getStatus());
        }
        if (request.getTeamId() != null) {
            // 队伍变更时的逻辑验证
            if (player.getStatus() == 3 && request.getTeamId() != null) {
                throw new BusinessException(400, "自由人不能属于任何队伍");
            }
            if (player.getStatus() != 3 && request.getTeamId() == null) {
                throw new BusinessException(400, "在职选手必须属于某个队伍");
            }
            player.setTeamId(request.getTeamId());
        }

        playerMapper.updateById(player);
        return player;
    }

    @Override
    public List<PlayerDepositLedgerVO> listPlayerDepositLedgers(Long playerId, Integer isVoided, Integer limit) {
        List<PlayerDepositLedger> ledgers = depositLedgerMapper.selectList(new LambdaQueryWrapper<PlayerDepositLedger>()
                .eq(playerId != null, PlayerDepositLedger::getPlayerId, playerId)
                .eq(isVoided != null, PlayerDepositLedger::getIsVoided, isVoided)
                .eq(PlayerDepositLedger::getDeleted, 0)
                .orderByDesc(PlayerDepositLedger::getCreatedAt)
                .last("LIMIT " + normalizeLimit(limit)));

        if (ledgers.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> playerIds = ledgers.stream().map(PlayerDepositLedger::getPlayerId).collect(Collectors.toSet());
        Map<Long, Player> players = playerMapper.selectBatchIds(playerIds).stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));

        Set<Long> teamIds = players.values().stream()
                .map(Player::getTeamId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, Team> teams = teamIds.isEmpty() ? Collections.emptyMap() :
                teamMapper.selectBatchIds(teamIds).stream()
                        .collect(Collectors.toMap(Team::getId, Function.identity()));

        return ledgers.stream().map(ledger -> {
            PlayerDepositLedgerVO vo = new PlayerDepositLedgerVO();
            vo.setId(ledger.getId());
            vo.setPlayerId(ledger.getPlayerId());
            Player player = players.get(ledger.getPlayerId());
            vo.setPlayerName(player != null ? player.getName() : "");
            vo.setTeamId(player != null ? player.getTeamId() : null);
            if (player != null && player.getTeamId() != null) {
                Team team = teams.get(player.getTeamId());
                vo.setTeamState(team != null ? team.getState() : "");
            }
            vo.setMatchId(ledger.getMatchId());
            vo.setResultId(ledger.getResultId());
            vo.setType(ledger.getType());
            vo.setAmount(ledger.getAmount());
            vo.setReason(ledger.getReason());
            vo.setBalanceBefore(ledger.getBalanceBefore());
            vo.setBalanceAfter(ledger.getBalanceAfter());
            vo.setSource(ledger.getSource());
            vo.setOperator(ledger.getOperator());
            vo.setIsVoided(ledger.getIsVoided());
            vo.setCreatedAt(ledger.getCreatedAt() != null ? ledger.getCreatedAt().toString() : null);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void voidPlayerDepositLedger(Long ledgerId, String reason) {
        if (ledgerId == null) {
            throw new BusinessException(400, "流水ID不能为空");
        }
        PlayerDepositLedger ledger = depositLedgerMapper.selectById(ledgerId);
        if (ledger == null) {
            throw new BusinessException(404, "流水记录不存在");
        }
        if (ledger.getIsVoided() == 1) {
            throw new BusinessException(400, "该流水已被撤回");
        }

        // 查找该选手在该流水之后的所有未撤回记录
        List<PlayerDepositLedger> laterLedgers = depositLedgerMapper.selectList(
                new LambdaQueryWrapper<PlayerDepositLedger>()
                        .eq(PlayerDepositLedger::getPlayerId, ledger.getPlayerId())
                        .gt(PlayerDepositLedger::getCreatedAt, ledger.getCreatedAt())
                        .eq(PlayerDepositLedger::getDeleted, 0)
                        .eq(PlayerDepositLedger::getIsVoided, 0)
                        .orderByAsc(PlayerDepositLedger::getCreatedAt)
        );

        // 将该流水及所有后续流水标记为撤回
        ledger.setIsVoided(1);
        ledger.setVoidedAt(java.time.LocalDateTime.now());
        ledger.setVoidReason(reason);
        depositLedgerMapper.updateById(ledger);

        for (PlayerDepositLedger laterLedger : laterLedgers) {
            laterLedger.setIsVoided(1);
            laterLedger.setVoidedAt(java.time.LocalDateTime.now());
            laterLedger.setVoidReason("关联撤回");
            depositLedgerMapper.updateById(laterLedger);
        }

        // 将选手存款恢复到该流水之前的值
        Player player = playerMapper.selectById(ledger.getPlayerId());
        if (player != null) {
            player.setDeposit(ledger.getBalanceBefore());
            playerMapper.updateById(player);
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 100;
        }
        return Math.max(1, Math.min(limit, 500));
    }
}
