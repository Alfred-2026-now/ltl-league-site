package com.ltl.league.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ltl.league.entity.Team;

import java.util.List;

public interface TeamService extends IService<Team> {

    List<Team> getAllTeams();

    /** 当前赛季战队（公开战队列表等场景使用） */
    List<Team> getCurrentSeasonTeams();

    Team getByState(String state);
}
