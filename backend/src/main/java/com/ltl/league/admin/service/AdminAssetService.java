package com.ltl.league.admin.service;

import com.ltl.league.admin.dto.AssetOverviewVO;
import com.ltl.league.admin.dto.LeagueAssetAdjustRequest;
import com.ltl.league.admin.dto.LeagueAssetLedgerVO;

import java.util.List;

public interface AdminAssetService {

    AssetOverviewVO getOverview(Integer days);

    List<LeagueAssetLedgerVO> listLeagueAssetLedgers(Integer limit);

    LeagueAssetLedgerVO manualAdjust(LeagueAssetAdjustRequest request);

    void recordIncome(
            Integer amount,
            String type,
            String reason,
            String source,
            String refTable,
            Long refId,
            Long matchId,
            Long resultId,
            String operator);

    void recordReversal(
            Integer amount,
            String type,
            String reason,
            String source,
            String refTable,
            Long refId,
            Long matchId,
            Long resultId,
            String operator);

    void reverseMatchResult(Long resultId, String reason);
}
