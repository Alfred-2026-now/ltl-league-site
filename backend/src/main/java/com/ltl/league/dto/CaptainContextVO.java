package com.ltl.league.dto;

import lombok.Data;

import java.util.List;

/**
 * 队长管理页面上下文数据
 */
@Data
public class CaptainContextVO {

    private Long teamId;
    private String teamState;
    private String teamName;
    private Integer teamPCoins;
    private String logoUrl;
    private String description;
    private Integer captainDeposit;
    private List<TeamMemberVO> members;

    @Data
    public static class TeamMemberVO {
        private Long id;
        private String name;
        private String position;
        private Integer value;
        private Integer deposit;
        private Integer isSubstitute;
        /** 是否队长（role=2） */
        private Integer isCaptain;
    }
}
