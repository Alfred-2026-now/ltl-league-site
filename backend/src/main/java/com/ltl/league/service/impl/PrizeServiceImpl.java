package com.ltl.league.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.service.AdminAssetService;
import com.ltl.league.dto.CreatePrizeRequest;
import com.ltl.league.dto.ExchangePrizeRequest;
import com.ltl.league.dto.PrizeExchangeVO;
import com.ltl.league.dto.PrizeVO;
import com.ltl.league.dto.UpdatePrizeRequest;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.PlayerDepositLedger;
import com.ltl.league.entity.Prize;
import com.ltl.league.entity.PrizeExchange;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.PlayerDepositLedgerMapper;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.mapper.PrizeExchangeMapper;
import com.ltl.league.mapper.PrizeMapper;
import com.ltl.league.service.PrizeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PrizeServiceImpl implements PrizeService {

    private final PrizeMapper prizeMapper;
    private final PrizeExchangeMapper prizeExchangeMapper;
    private final PlayerMapper playerMapper;
    private final PlayerDepositLedgerMapper depositLedgerMapper;
    private final AdminAssetService adminAssetService;

    public PrizeServiceImpl(
            PrizeMapper prizeMapper,
            PrizeExchangeMapper prizeExchangeMapper,
            PlayerMapper playerMapper,
            PlayerDepositLedgerMapper depositLedgerMapper,
            AdminAssetService adminAssetService) {
        this.prizeMapper = prizeMapper;
        this.prizeExchangeMapper = prizeExchangeMapper;
        this.playerMapper = playerMapper;
        this.depositLedgerMapper = depositLedgerMapper;
        this.adminAssetService = adminAssetService;
    }

    @Override
    public List<PrizeVO> listActivePrizes() {
        List<Prize> prizes = prizeMapper.selectList(new LambdaQueryWrapper<Prize>()
                .eq(Prize::getIsActive, 1)
                .eq(Prize::getDeleted, 0)
                .orderByDesc(Prize::getCreatedAt));

        return prizes.stream().map(this::toPrizeVO).collect(Collectors.toList());
    }

    @Override
    public List<PrizeVO> listAllPrizes() {
        List<Prize> prizes = prizeMapper.selectList(new LambdaQueryWrapper<Prize>()
                .eq(Prize::getDeleted, 0)
                .orderByDesc(Prize::getCreatedAt));

        return prizes.stream().map(this::toPrizeVO).collect(Collectors.toList());
    }

    @Override
    public PrizeVO getPrize(Long id) {
        Prize prize = prizeMapper.selectById(id);
        if (prize == null || prize.getDeleted() == 1) {
            throw new BusinessException(404, "奖品不存在");
        }
        return toPrizeVO(prize);
    }

    @Override
    @Transactional
    public PrizeVO createPrize(CreatePrizeRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求参数不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException(400, "奖品名称不能为空");
        }
        if (request.getCostPoints() == null || request.getCostPoints() <= 0) {
            throw new BusinessException(400, "兑换积分必须大于0");
        }
        if (request.getStock() == null || request.getStock() < 0) {
            throw new BusinessException(400, "库存不能小于0");
        }

        Prize prize = new Prize();
        prize.setName(request.getName().trim());
        prize.setDescription(request.getDescription());
        prize.setImageUrl(request.getImageUrl());
        prize.setCostPoints(request.getCostPoints());
        prize.setStock(request.getStock());
        prize.setIsActive(1);

        prizeMapper.insert(prize);
        return toPrizeVO(prize);
    }

    @Override
    @Transactional
    public PrizeVO updatePrize(Long id, UpdatePrizeRequest request) {
        if (id == null) {
            throw new BusinessException(400, "奖品ID不能为空");
        }
        if (request == null) {
            throw new BusinessException(400, "请求参数不能为空");
        }

        Prize prize = prizeMapper.selectById(id);
        if (prize == null || prize.getDeleted() == 1) {
            throw new BusinessException(404, "奖品不存在");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            prize.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            prize.setDescription(request.getDescription());
        }
        if (request.getImageUrl() != null) {
            prize.setImageUrl(request.getImageUrl());
        }
        if (request.getCostPoints() != null && request.getCostPoints() > 0) {
            prize.setCostPoints(request.getCostPoints());
        }
        if (request.getStock() != null && request.getStock() >= 0) {
            prize.setStock(request.getStock());
        }
        if (request.getIsActive() != null) {
            prize.setIsActive(request.getIsActive());
        }

        prizeMapper.updateById(prize);
        return toPrizeVO(prize);
    }

    @Override
    @Transactional
    public void deletePrize(Long id) {
        if (id == null) {
            throw new BusinessException(400, "奖品ID不能为空");
        }
        Prize prize = prizeMapper.selectById(id);
        if (prize == null || prize.getDeleted() == 1) {
            throw new BusinessException(404, "奖品不存在");
        }

        prizeMapper.deleteById(id);
    }

    @Override
    @Transactional
    public PrizeExchangeVO exchangePrize(ExchangePrizeRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求参数不能为空");
        }
        if (request.getPrizeId() == null) {
            throw new BusinessException(400, "奖品ID不能为空");
        }
        if (request.getPlayerName() == null || request.getPlayerName().isBlank()) {
            throw new BusinessException(400, "选手名称不能为空");
        }

        Prize prize = prizeMapper.selectById(request.getPrizeId());
        if (prize == null || prize.getDeleted() == 1) {
            throw new BusinessException(404, "奖品不存在");
        }
        if (prize.getIsActive() == 0) {
            throw new BusinessException(400, "该奖品已下架");
        }
        if (prize.getStock() <= 0) {
            throw new BusinessException(400, "该奖品库存不足");
        }

        Player player = playerMapper.selectOne(new LambdaQueryWrapper<Player>()
                .eq(Player::getName, request.getPlayerName().trim())
                .eq(Player::getDeleted, 0));

        if (player == null) {
            throw new BusinessException(404, "选手不存在");
        }

        Integer currentDeposit = player.getDeposit() != null ? player.getDeposit() : 0;
        if (currentDeposit < prize.getCostPoints()) {
            throw new BusinessException(400, "选手积分不足");
        }

        Integer newDeposit = currentDeposit - prize.getCostPoints();

        PrizeExchange exchange = new PrizeExchange();
        exchange.setPrizeId(prize.getId());
        exchange.setPlayerId(player.getId());
        exchange.setPlayerName(player.getName());
        exchange.setCostPoints(prize.getCostPoints());
        exchange.setStatus("pending");
        exchange.setContactInfo(request.getContactInfo());
        exchange.setRemark(request.getRemark());
        prizeExchangeMapper.insert(exchange);

        PlayerDepositLedger ledger = new PlayerDepositLedger();
        ledger.setPlayerId(player.getId());
        ledger.setType("prize_exchange");
        ledger.setAmount(-prize.getCostPoints());
        ledger.setReason("兑换奖品：" + prize.getName());
        ledger.setBalanceBefore(currentDeposit);
        ledger.setBalanceAfter(newDeposit);
        ledger.setSource("prize_exchange");
        ledger.setOperator("system");
        ledger.setIsVoided(0);
        depositLedgerMapper.insert(ledger);
        adminAssetService.recordIncome(
                prize.getCostPoints(),
                "prize_exchange",
                "兑换奖品：" + prize.getName(),
                "prize_exchange",
                "prize_exchanges",
                exchange.getId(),
                null,
                null,
                "system");

        prize.setStock(prize.getStock() - 1);
        prizeMapper.updateById(prize);

        player.setDeposit(newDeposit);
        playerMapper.updateById(player);

        return toPrizeExchangeVO(exchange, prize.getName());
    }

    @Override
    public List<PrizeExchangeVO> listExchanges(String status, Integer limit) {
        List<PrizeExchange> exchanges = prizeExchangeMapper.selectList(new LambdaQueryWrapper<PrizeExchange>()
                .eq(status != null && !status.isBlank(), PrizeExchange::getStatus, status)
                .eq(PrizeExchange::getDeleted, 0)
                .orderByDesc(PrizeExchange::getCreatedAt)
                .last("LIMIT " + normalizeLimit(limit)));

        if (exchanges.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> prizeIds = exchanges.stream().map(PrizeExchange::getPrizeId).collect(Collectors.toSet());
        Map<Long, Prize> prizes = prizeMapper.selectBatchIds(prizeIds).stream()
                .collect(Collectors.toMap(Prize::getId, Function.identity()));

        return exchanges.stream().map(exchange -> {
            Prize prize = prizes.get(exchange.getPrizeId());
            String prizeName = prize != null ? prize.getName() : "";
            return toPrizeExchangeVO(exchange, prizeName);
        }).collect(Collectors.toList());
    }

    @Override
    public PrizeExchangeVO getExchange(Long id) {
        if (id == null) {
            throw new BusinessException(400, "兑换记录ID不能为空");
        }
        PrizeExchange exchange = prizeExchangeMapper.selectById(id);
        if (exchange == null || exchange.getDeleted() == 1) {
            throw new BusinessException(404, "兑换记录不存在");
        }

        Prize prize = prizeMapper.selectById(exchange.getPrizeId());
        String prizeName = prize != null ? prize.getName() : "";

        return toPrizeExchangeVO(exchange, prizeName);
    }

    @Override
    @Transactional
    public void processExchange(Long exchangeId, String processedBy) {
        if (exchangeId == null) {
            throw new BusinessException(400, "兑换记录ID不能为空");
        }

        PrizeExchange exchange = prizeExchangeMapper.selectById(exchangeId);
        if (exchange == null || exchange.getDeleted() == 1) {
            throw new BusinessException(404, "兑换记录不存在");
        }
        if (!"pending".equals(exchange.getStatus())) {
            throw new BusinessException(400, "该兑换记录已被处理");
        }

        exchange.setStatus("completed");
        exchange.setProcessedAt(LocalDateTime.now());
        exchange.setProcessedBy(processedBy);
        prizeExchangeMapper.updateById(exchange);
    }

    @Override
    @Transactional
    public void cancelExchange(Long exchangeId, String reason) {
        if (exchangeId == null) {
            throw new BusinessException(400, "兑换记录ID不能为空");
        }

        PrizeExchange exchange = prizeExchangeMapper.selectById(exchangeId);
        if (exchange == null || exchange.getDeleted() == 1) {
            throw new BusinessException(404, "兑换记录不存在");
        }
        if (!"pending".equals(exchange.getStatus())) {
            throw new BusinessException(400, "只能取消待处理的兑换记录");
        }

        exchange.setStatus("cancelled");
        exchange.setRemark((exchange.getRemark() != null ? exchange.getRemark() + "; " : "") + "取消原因: " + reason);
        prizeExchangeMapper.updateById(exchange);

        Prize prize = prizeMapper.selectById(exchange.getPrizeId());
        if (prize != null) {
            prize.setStock(prize.getStock() + 1);
            prizeMapper.updateById(prize);
        }

        Player player = playerMapper.selectById(exchange.getPlayerId());
        if (player != null) {
            Integer currentDeposit = player.getDeposit() != null ? player.getDeposit() : 0;
            Integer newDeposit = currentDeposit + exchange.getCostPoints();

            PlayerDepositLedger ledger = new PlayerDepositLedger();
            ledger.setPlayerId(player.getId());
            ledger.setType("prize_exchange_refund");
            ledger.setAmount(exchange.getCostPoints());
            ledger.setReason("取消兑换退款");
            ledger.setBalanceBefore(currentDeposit);
            ledger.setBalanceAfter(newDeposit);
            ledger.setSource("prize_exchange_refund");
            ledger.setOperator("admin");
            ledger.setIsVoided(0);
            depositLedgerMapper.insert(ledger);
            adminAssetService.recordReversal(
                    exchange.getCostPoints(),
                    "prize_exchange_refund",
                    "取消兑换退款，回滚联盟资产",
                    "prize_exchange_refund",
                    "prize_exchanges",
                    exchange.getId(),
                    null,
                    null,
                    "admin");

            player.setDeposit(newDeposit);
            playerMapper.updateById(player);
        }
    }

    private PrizeVO toPrizeVO(Prize prize) {
        PrizeVO vo = new PrizeVO();
        vo.setId(prize.getId());
        vo.setName(prize.getName());
        vo.setDescription(prize.getDescription());
        vo.setImageUrl(prize.getImageUrl());
        vo.setCostPoints(prize.getCostPoints());
        vo.setStock(prize.getStock());
        vo.setIsActive(prize.getIsActive());
        vo.setCreatedAt(prize.getCreatedAt() != null ? prize.getCreatedAt().toString() : null);
        return vo;
    }

    private PrizeExchangeVO toPrizeExchangeVO(PrizeExchange exchange, String prizeName) {
        PrizeExchangeVO vo = new PrizeExchangeVO();
        vo.setId(exchange.getId());
        vo.setPrizeId(exchange.getPrizeId());
        vo.setPrizeName(prizeName);
        vo.setPlayerId(exchange.getPlayerId());
        vo.setPlayerName(exchange.getPlayerName());
        vo.setCostPoints(exchange.getCostPoints());
        vo.setStatus(exchange.getStatus());
        vo.setContactInfo(exchange.getContactInfo());
        vo.setRemark(exchange.getRemark());
        vo.setProcessedAt(exchange.getProcessedAt() != null ? exchange.getProcessedAt().toString() : null);
        vo.setProcessedBy(exchange.getProcessedBy());
        vo.setCreatedAt(exchange.getCreatedAt() != null ? exchange.getCreatedAt().toString() : null);
        return vo;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 100;
        }
        return Math.max(1, Math.min(limit, 500));
    }
}
