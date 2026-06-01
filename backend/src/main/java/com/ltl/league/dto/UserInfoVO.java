package com.ltl.league.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO {

    private Long playerId;
    private String playerName;
    private Integer role;
    private String roleName;

    private Long teamId;
    private String teamName;
    private String teamState;

    private Integer value;
    private Integer deposit;
    private String position;
    private Integer isSubstitute;
}
