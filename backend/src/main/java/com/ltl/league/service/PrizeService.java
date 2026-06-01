package com.ltl.league.service;

import com.ltl.league.dto.CreatePrizeRequest;
import com.ltl.league.dto.ExchangePrizeRequest;
import com.ltl.league.dto.PrizeExchangeVO;
import com.ltl.league.dto.PrizeVO;
import com.ltl.league.dto.UpdatePrizeRequest;

import java.util.List;

public interface PrizeService {

    List<PrizeVO> listActivePrizes();

    List<PrizeVO> listAllPrizes();

    PrizeVO getPrize(Long id);

    PrizeVO createPrize(CreatePrizeRequest request);

    PrizeVO updatePrize(Long id, UpdatePrizeRequest request);

    void deletePrize(Long id);

    PrizeExchangeVO exchangePrize(ExchangePrizeRequest request);

    List<PrizeExchangeVO> listExchanges(String status, Integer limit);

    PrizeExchangeVO getExchange(Long id);

    void processExchange(Long exchangeId, String processedBy);

    void cancelExchange(Long exchangeId, String reason);
}
