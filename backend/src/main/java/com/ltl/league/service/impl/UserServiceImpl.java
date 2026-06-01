package com.ltl.league.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.dto.ChangePasswordRequest;
import com.ltl.league.dto.PrizeExchangeDetailVO;
import com.ltl.league.dto.UserInfoVO;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.Prize;
import com.ltl.league.entity.PrizeExchange;
import com.ltl.league.entity.Team;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.mapper.PrizeExchangeMapper;
import com.ltl.league.mapper.PrizeMapper;
import com.ltl.league.mapper.TeamMapper;
import com.ltl.league.service.UserService;
import com.ltl.league.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final PlayerMapper playerMapper;
    private final TeamMapper teamMapper;
    private final PrizeExchangeMapper prizeExchangeMapper;
    private final PrizeMapper prizeMapper;
    private final PasswordUtil passwordUtil;

    private static final Integer ROLE_USER = 0;
    private static final Integer ROLE_ADMIN = 1;
    private static final String[] DEFAULT_ADMINS = {"天下人", "陶吉吉", "大橙子"};

    @Override
    public UserInfoVO getUserInfo(Long playerId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new IllegalArgumentException("选手不存在");
        }

        Team team = null;
        if (player.getTeamId() != null) {
            team = teamMapper.selectById(player.getTeamId());
        }

        Integer role = determineRole(player);

        return UserInfoVO.builder()
                .playerId(player.getId())
                .playerName(player.getName())
                .role(role)
                .roleName(role == ROLE_ADMIN ? "管理员" : "普通用户")
                .teamId(team != null ? team.getId() : null)
                .teamName(team != null ? team.getName() : null)
                .teamState(team != null ? team.getState() : null)
                .value(player.getValue())
                .deposit(player.getDeposit())
                .position(player.getPosition())
                .isSubstitute(player.getIsSubstitute())
                .build();
    }

    @Override
    public List<PrizeExchangeDetailVO> getUserPrizeExchanges(Long playerId) {
        LambdaQueryWrapper<PrizeExchange> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrizeExchange::getPlayerId, playerId);
        wrapper.orderByDesc(PrizeExchange::getCreatedAt);

        List<PrizeExchange> exchanges = prizeExchangeMapper.selectList(wrapper);

        return exchanges.stream().map(this::convertToDetailVO).collect(Collectors.toList());
    }

    private PrizeExchangeDetailVO convertToDetailVO(PrizeExchange exchange) {
        // 查询奖品名称
        Prize prize = prizeMapper.selectById(exchange.getPrizeId());
        String prizeName = prize != null ? prize.getName() : null;

        return PrizeExchangeDetailVO.builder()
                .id(exchange.getId())
                .prizeId(exchange.getPrizeId())
                .prizeName(prizeName)
                .costPoints(exchange.getCostPoints())
                .status(exchange.getStatus())
                .statusText(getStatusText(exchange.getStatus()))
                .contactInfo(exchange.getContactInfo())
                .remark(exchange.getRemark())
                .processedAt(exchange.getProcessedAt())
                .processedBy(exchange.getProcessedBy())
                .createdAt(exchange.getCreatedAt())
                .build();
    }

    private String getStatusText(String status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case "pending":
                return "待处理";
            case "approved":
                return "已批准";
            case "rejected":
                return "已拒绝";
            case "completed":
                return "已完成";
            default:
                return status;
        }
    }

    private Integer determineRole(Player player) {
        if (player.getRole() != null) {
            return player.getRole();
        }

        for (String adminName : DEFAULT_ADMINS) {
            if (adminName.equals(player.getName())) {
                return ROLE_ADMIN;
            }
        }

        return ROLE_USER;
    }

    @Override
    public void changePassword(Long playerId, ChangePasswordRequest request) {
        // 验证新密码和确认密码是否一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("新密码和确认密码不一致");
        }

        // 验证新密码长度
        if (request.getNewPassword().length() < 6) {
            throw new IllegalArgumentException("新密码长度不能少于6位");
        }

        // 获取用户信息
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        // 验证当前密码
        if (!passwordUtil.verifyPassword(request.getCurrentPassword(), player.getPassword())) {
            throw new IllegalArgumentException("当前密码错误");
        }

        // 更新密码
        String encryptedPassword = passwordUtil.encryptPassword(request.getNewPassword());
        player.setPassword(encryptedPassword);
        playerMapper.updateById(player);

        log.info("用户 {} 修改密码成功", player.getName());
    }
}
