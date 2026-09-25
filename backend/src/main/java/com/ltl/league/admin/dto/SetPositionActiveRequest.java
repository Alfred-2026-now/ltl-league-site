package com.ltl.league.admin.dto;

import lombok.Data;

/**
 * 手动设置某个位置身价的"激活/未激活"状态。
 * position: TOP / JUG / MID / BOT / SUP
 * active: 1=已激活, 0=未激活
 */
@Data
public class SetPositionActiveRequest {
    private String position;
    private Integer active;
}
