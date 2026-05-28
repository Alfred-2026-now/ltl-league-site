package com.ltl.league.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ltl.league.entity.Team;
import com.ltl.league.mapper.TeamMapper;
import com.ltl.league.service.TeamService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team> implements TeamService {

    @Override
    public List<Team> getAllTeams() {
        return lambdaQuery().list();
    }

    @Override
    public Team getByState(String state) {
        return lambdaQuery()
                .eq(Team::getState, state)
                .one();
    }
}
