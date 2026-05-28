package com.ltl.league.service;

import com.ltl.league.dto.MatchVO;
import com.ltl.league.entity.Match;

import java.util.List;

public interface MatchService {
    List<Match> getAllMatches();
    Match getMatchById(Long id);
    List<MatchVO> getAllMatchVOs();
    MatchVO getMatchVOById(Long id);
}
