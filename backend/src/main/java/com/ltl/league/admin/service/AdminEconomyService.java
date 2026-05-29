package com.ltl.league.admin.service;

import com.ltl.league.admin.dto.AdminPLedgerVO;
import com.ltl.league.admin.dto.AdminValuationChangeVO;
import com.ltl.league.admin.dto.ManualValuationAdjustRequest;

import java.util.List;

public interface AdminEconomyService {

    List<AdminPLedgerVO> listPLedgers(Long teamId, Long matchId, String type, Integer isVoided, String source, Integer limit);

    List<AdminValuationChangeVO> listValuationChanges(Long playerId, Long teamId, Long matchId, String source, Integer isVoided, Integer limit);

    AdminValuationChangeVO manualAdjustment(ManualValuationAdjustRequest request);
}
