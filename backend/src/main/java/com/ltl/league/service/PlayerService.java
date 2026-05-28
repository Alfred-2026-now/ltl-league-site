package com.ltl.league.service;

import com.ltl.league.entity.Player;

import java.util.List;

public interface PlayerService {
    List<Player> getAllPlayers();
    List<Player> getPlayersByTeamId(Long teamId);
}
