package com.ltl.league.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ltl.league.entity.Team;

import java.util.List;

public interface TeamService extends IService<Team> {

    List<Team> getAllTeams();

    Team getByState(String state);
}
