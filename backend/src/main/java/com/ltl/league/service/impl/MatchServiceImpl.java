package com.ltl.league.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ltl.league.entity.Match;
import com.ltl.league.mapper.MatchMapper;
import com.ltl.league.service.MatchService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MatchServiceImpl extends ServiceImpl<MatchMapper, Match> implements MatchService {

    @Override
    public List<Match> getAllMatches() {
        return lambdaQuery()
                .orderByAsc(Match::getMatchDate)
                .list();
    }

    @Override
    public Match getMatchById(Long id) {
        return getById(id);
    }
}
