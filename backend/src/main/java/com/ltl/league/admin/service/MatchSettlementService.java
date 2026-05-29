package com.ltl.league.admin.service;

import com.ltl.league.admin.dto.SettlementInputDTO;
import com.ltl.league.admin.dto.SettlementPreviewVO;
import com.ltl.league.entity.Match;
import com.ltl.league.entity.MatchResult;

public interface MatchSettlementService {

    SettlementPreviewVO preview(Match match, MatchResult result, SettlementInputDTO settlement);

    void apply(Match match, MatchResult result);

    void rollback(Match match, MatchResult result);

    void syncInputs(Match match, MatchResult result, SettlementInputDTO settlement);

    SettlementInputDTO loadInputs(Long matchId, Long resultId);
}
