package com.ltl.league.dto;

import lombok.Data;

/**
 * 队长更新队伍信息请求
 */
@Data
public class CaptainUpdateTeamInfoRequest {

    /** 队伍简介（可为空表示不改） */
    private String description;

    /** 队伍名称（可选，不传则不改） */
    private String name;

    /** 队伍简写/国名（可选，不传则不改） */
    private String state;
}
