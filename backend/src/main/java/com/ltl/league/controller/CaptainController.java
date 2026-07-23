package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.dto.CaptainContextVO;
import com.ltl.league.dto.CaptainDepositToTeamRequest;
import com.ltl.league.dto.CaptainPLedgerPageVO;
import com.ltl.league.dto.CaptainPaySalaryRequest;
import com.ltl.league.dto.CaptainUpdateTeamInfoRequest;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.service.CaptainService;
import com.ltl.league.util.AuthUtil;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 队长功能接口（仅 role=2 队长可访问，由 Service 层校验）
 */
@RestController
@RequestMapping("/captain")
public class CaptainController {

    private static final String COOKIE_NAME = "ltl_auth";

    private final CaptainService captainService;
    private final AuthUtil authUtil;

    public CaptainController(CaptainService captainService, AuthUtil authUtil) {
        this.captainService = captainService;
        this.authUtil = authUtil;
    }

    /** 获取队长管理页面上下文（队伍信息 + 队员列表） */
    @GetMapping("/context")
    public Result<CaptainContextVO> getContext(@CookieValue(value = COOKIE_NAME, required = false) String token) {
        Long playerId = requireLogin(token);
        return Result.success(captainService.getContext(playerId));
    }

    /** 分页查询本队 P 币流水 */
    @GetMapping("/p-ledger")
    public Result<CaptainPLedgerPageVO> listTeamPLedgers(
            @CookieValue(value = COOKIE_NAME, required = false) String token,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        Long playerId = requireLogin(token);
        return Result.success(captainService.listTeamPLedgers(playerId, page, pageSize));
    }

    /** 队长从队伍资金给指定队员发工资 */
    @PostMapping("/pay-salary")
    public Result<Void> paySalary(@CookieValue(value = COOKIE_NAME, required = false) String token,
                                  @RequestBody CaptainPaySalaryRequest request) {
        Long playerId = requireLogin(token);
        captainService.paySalary(playerId, request);
        return Result.success();
    }

    /** 队长更新队伍简介/名称/简写 */
    @PutMapping("/team-info")
    public Result<Void> updateTeamInfo(@CookieValue(value = COOKIE_NAME, required = false) String token,
                                       @RequestBody CaptainUpdateTeamInfoRequest request) {
        Long playerId = requireLogin(token);
        captainService.updateTeamInfo(playerId, request);
        return Result.success();
    }

    /** 队长上传队伍图标，返回图片URL */
    @PostMapping("/team-logo")
    public Result<Map<String, String>> uploadTeamLogo(@CookieValue(value = COOKIE_NAME, required = false) String token,
                                                      @RequestParam("file") MultipartFile file) {
        Long playerId = requireLogin(token);
        String url = captainService.uploadTeamLogo(playerId, file);
        Map<String, String> result = new HashMap<>();
        result.put("url", url);
        return Result.success(result);
    }

    /** 队长个人账户向队伍账户转入资金（无税率） */
    @PostMapping("/deposit-to-team")
    public Result<Void> depositToTeam(@CookieValue(value = COOKIE_NAME, required = false) String token,
                                      @RequestBody CaptainDepositToTeamRequest request) {
        Long playerId = requireLogin(token);
        captainService.depositToTeam(playerId, request);
        return Result.success();
    }

    private Long requireLogin(String token) {
        if (token == null || token.isEmpty()) {
            throw new BusinessException(401, "未登录");
        }
        AuthUtil.CookieData data = authUtil.parseCookieValue(token);
        if (data == null) {
            throw new BusinessException(401, "登录已过期");
        }
        return data.getPlayerId();
    }
}
