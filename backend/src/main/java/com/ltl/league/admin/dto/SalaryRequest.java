package com.ltl.league.admin.dto;

import lombok.Data;

@Data
public class SalaryRequest {
    private Integer rate;

    // 可选：指定分组（登峰组/涅槃组/麦兜组）只给该组在职队员发工资；不传则给全部在职队员发
    private Long teamId;

    // 用于区分是选手发工资还是队伍扣除工资
    // 如果为 true，则扣除队伍工资；false 或不传则发放选手工资
    private Boolean deductTeam;
}
