package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class UpdatePlayerRequest {
    private Long teamId;
    private String name;
    private Integer value;
    private String position;
    private String gameAccount;
    private String puuid;
    private Integer isSubstitute;
    private Integer status;
}
