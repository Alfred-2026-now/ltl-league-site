package com.ltl.league.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ltl.league.entity.Player;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.service.PlayerService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlayerServiceImpl extends ServiceImpl<PlayerMapper, Player> implements PlayerService {

    @Override
    public List<Player> getAllPlayers() {
        return lambdaQuery().list();
    }

    @Override
    public List<Player> getPlayersByTeamId(Long teamId) {
        return lambdaQuery()
                .eq(Player::getTeamId, teamId)
                .list();
    }
}
