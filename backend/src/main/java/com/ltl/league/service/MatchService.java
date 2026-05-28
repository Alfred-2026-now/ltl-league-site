package com.ltl.league.service;

import com.ltl.league.entity.Match;

import java.util.List;

public interface MatchService {
    List<Match> getAllMatches();
    Match getMatchById(Long id);
}
