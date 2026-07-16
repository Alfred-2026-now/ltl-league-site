package com.ltl.league.service;

import com.ltl.league.dto.CaptainContextVO;
import com.ltl.league.dto.CaptainDepositToTeamRequest;
import com.ltl.league.dto.CaptainPaySalaryRequest;
import com.ltl.league.dto.CaptainUpdateTeamInfoRequest;
import org.springframework.web.multipart.MultipartFile;

/**
 * 队长功能服务
 */
public interface CaptainService {

    /**
     * 获取队长管理页面上下文（队伍信息 + 队员列表）
     */
    CaptainContextVO getContext(Long captainPlayerId);

    /**
     * 队长从队伍资金给指定队员发工资（无手续费）
     */
    void paySalary(Long captainPlayerId, CaptainPaySalaryRequest request);

    /**
     * 队长更新队伍简介/名称/简写
     */
    void updateTeamInfo(Long captainPlayerId, CaptainUpdateTeamInfoRequest request);

    /**
     * 队长上传队伍图标，返回图片URL
     */
    String uploadTeamLogo(Long captainPlayerId, MultipartFile file);

    /**
     * 队长个人账户向队伍账户转入资金（无税率）
     */
    void depositToTeam(Long captainPlayerId, CaptainDepositToTeamRequest request);
}
