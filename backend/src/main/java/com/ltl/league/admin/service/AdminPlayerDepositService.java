package com.ltl.league.admin.service;

import com.ltl.league.admin.dto.*;
import com.ltl.league.entity.Player;

import java.util.List;

public interface AdminPlayerDepositService {
    void adjustPlayerDeposit(AdjustPlayerDepositRequest request);

    void addLoanFeeToPlayer(Long playerId, Integer amount);

    Player createPlayer(CreatePlayerRequest request);

    Player updatePlayer(Long playerId, UpdatePlayerRequest request);

    List<PlayerDepositLedgerVO> listPlayerDepositLedgers(Long playerId, Integer isVoided, Integer limit);

    void voidPlayerDepositLedger(Long ledgerId, String reason);
}
