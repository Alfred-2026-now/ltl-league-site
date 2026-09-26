package com.ltl.league.dto;

import com.ltl.league.entity.Player;
import lombok.Data;

@Data
public class PublicPlayerVO {
    private Long id;
    private Long teamId;
    private String name;
    private Integer value;
    private Integer topValue;
    private Integer jugValue;
    private Integer midValue;
    private Integer botValue;
    private Integer supValue;
    private Integer topActive;
    private Integer jugActive;
    private Integer midActive;
    private Integer botActive;
    private Integer supActive;
    private Integer maxValue;
    private String position;
    private Integer isSubstitute;
    private Integer isLoan;
    private Long loanTeamId;
    private Integer status;
    private Integer bounty;
    private Integer role;

    public static PublicPlayerVO from(Player player) {
        PublicPlayerVO view = new PublicPlayerVO();
        view.setId(player.getId());
        view.setTeamId(player.getTeamId());
        view.setName(player.getName());
        view.setValue(player.getValue());
        view.setTopValue(player.getTopValue());
        view.setJugValue(player.getJugValue());
        view.setMidValue(player.getMidValue());
        view.setBotValue(player.getBotValue());
        view.setSupValue(player.getSupValue());
        view.setTopActive(player.getTopActive());
        view.setJugActive(player.getJugActive());
        view.setMidActive(player.getMidActive());
        view.setBotActive(player.getBotActive());
        view.setSupActive(player.getSupActive());
        view.setMaxValue(player.getMaxValue());
        view.setPosition(player.getPosition());
        view.setIsSubstitute(player.getIsSubstitute());
        view.setIsLoan(player.getIsLoan());
        view.setLoanTeamId(player.getLoanTeamId());
        view.setStatus(player.getStatus());
        view.setBounty(player.getBounty());
        view.setRole(player.getRole());
        return view;
    }
}
