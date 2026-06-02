package com.ltl.league.service;

import com.ltl.league.dto.PlayerTransferPreviewVO;
import com.ltl.league.dto.PlayerTransferRequest;
import com.ltl.league.dto.PlayerTransferVO;

import java.util.List;

public interface PlayerTransferService {
    PlayerTransferPreviewVO preview(Long donorPlayerId, PlayerTransferRequest request);

    PlayerTransferVO transfer(Long donorPlayerId, PlayerTransferRequest request);

    List<PlayerTransferVO> listMine(Long donorPlayerId, Integer limit);
}
