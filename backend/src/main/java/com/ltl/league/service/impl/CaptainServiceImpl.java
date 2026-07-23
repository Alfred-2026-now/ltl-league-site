package com.ltl.league.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.dto.CaptainContextVO;
import com.ltl.league.dto.CaptainDepositToTeamRequest;
import com.ltl.league.dto.CaptainPLedgerPageVO;
import com.ltl.league.dto.CaptainPLedgerVO;
import com.ltl.league.dto.CaptainPaySalaryRequest;
import com.ltl.league.dto.CaptainUpdateTeamInfoRequest;
import com.ltl.league.entity.PLedger;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.PlayerDepositLedger;
import com.ltl.league.entity.Team;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.PLedgerMapper;
import com.ltl.league.mapper.PlayerDepositLedgerMapper;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.mapper.TeamMapper;
import com.ltl.league.service.CaptainService;
import com.ltl.league.util.ImageCompressUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 队长功能服务实现：队长管理页面上下文 + 从队伍资金发工资
 */
@Slf4j
@Service
public class CaptainServiceImpl implements CaptainService {

    private static final Integer ROLE_CAPTAIN = 2;
    private static final Integer STATUS_ACTIVE = 1;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PlayerMapper playerMapper;
    private final TeamMapper teamMapper;
    private final PLedgerMapper pLedgerMapper;
    private final PlayerDepositLedgerMapper depositLedgerMapper;

    @Value("${ltl.upload.dir}")
    private String uploadBaseDir;

    @Value("${ltl.upload.url-prefix}")
    private String uploadUrlPrefix;

    public CaptainServiceImpl(PlayerMapper playerMapper, TeamMapper teamMapper,
                              PLedgerMapper pLedgerMapper, PlayerDepositLedgerMapper depositLedgerMapper) {
        this.playerMapper = playerMapper;
        this.teamMapper = teamMapper;
        this.pLedgerMapper = pLedgerMapper;
        this.depositLedgerMapper = depositLedgerMapper;
    }

    @Override
    public CaptainContextVO getContext(Long captainPlayerId) {
        Player captain = playerMapper.selectById(captainPlayerId);
        if (captain == null) {
            throw new BusinessException(404, "选手不存在");
        }
        requireCaptainRole(captain);

        CaptainContextVO vo = new CaptainContextVO();
        vo.setCaptainDeposit(captain.getDeposit());

        // 队长尚未归属队伍：返回空壳，前端展示提示而非报错
        if (captain.getTeamId() == null) {
            vo.setMembers(Collections.emptyList());
            return vo;
        }
        Team team = teamMapper.selectById(captain.getTeamId());
        if (team == null) {
            vo.setMembers(Collections.emptyList());
            return vo;
        }
        List<Player> members = playerMapper.selectList(new LambdaQueryWrapper<Player>()
                .eq(Player::getTeamId, captain.getTeamId())
                .eq(Player::getStatus, STATUS_ACTIVE)
                .eq(Player::getDeleted, 0)
                .orderByAsc(Player::getIsSubstitute)
                .orderByAsc(Player::getId));

        vo.setTeamId(team.getId());
        vo.setTeamState(team.getState());
        vo.setTeamName(team.getName());
        vo.setTeamPCoins(team.getPCoins());
        vo.setLogoUrl(team.getLogoUrl());
        vo.setDescription(team.getDescription());
        vo.setMembers(members.stream().map(this::toMemberVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public CaptainPLedgerPageVO listTeamPLedgers(Long captainPlayerId, Integer page, Integer pageSize) {
        Player captain = requireCaptain(captainPlayerId);
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safeSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<PLedger> baseQuery = new LambdaQueryWrapper<PLedger>()
                .eq(PLedger::getTeamId, captain.getTeamId())
                .eq(PLedger::getIsVoided, 0);
        long total = pLedgerMapper.selectCount(baseQuery);

        int offset = (safePage - 1) * safeSize;
        List<PLedger> records = total == 0 || offset >= total
                ? Collections.emptyList()
                : pLedgerMapper.selectList(new LambdaQueryWrapper<PLedger>()
                .select(PLedger::getId, PLedger::getMatchId, PLedger::getType, PLedger::getAmount,
                        PLedger::getReason, PLedger::getVersion, PLedger::getBalanceBefore,
                        PLedger::getBalanceAfter, PLedger::getIsVoided, PLedger::getCreatedAt)
                .eq(PLedger::getTeamId, captain.getTeamId())
                .eq(PLedger::getIsVoided, 0)
                .orderByDesc(PLedger::getCreatedAt)
                .orderByDesc(PLedger::getId)
                .last("LIMIT " + offset + "," + safeSize));

        CaptainPLedgerPageVO pageVO = new CaptainPLedgerPageVO();
        pageVO.setPage(safePage);
        pageVO.setPageSize(safeSize);
        pageVO.setTotal(total);
        pageVO.setRecords(records.stream().map(this::toPLedgerVO).collect(Collectors.toList()));
        return pageVO;
    }

    @Override
    @Transactional
    public void paySalary(Long captainPlayerId, CaptainPaySalaryRequest request) {
        if (request == null || request.getTargetPlayerId() == null) {
            throw new BusinessException(400, "请选择发放目标");
        }
        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new BusinessException(400, "发放金额必须为正数");
        }
        Integer amount = request.getAmount();

        // 行锁依次锁定队长、队伍、目标选手，避免并发超发
        Player captain = playerMapper.selectByIdForUpdate(captainPlayerId);
        if (captain == null) {
            throw new BusinessException(404, "队长不存在");
        }
        requireCaptainRole(captain);
        if (captain.getStatus() == null || !captain.getStatus().equals(STATUS_ACTIVE)) {
            throw new BusinessException(400, "队长状态异常");
        }
        if (captain.getTeamId() == null) {
            throw new BusinessException(400, "队长尚未归属任何队伍");
        }

        Team team = teamMapper.selectByIdForUpdate(captain.getTeamId());
        if (team == null) {
            throw new BusinessException(404, "所属队伍不存在");
        }

        Player target = playerMapper.selectByIdForUpdate(request.getTargetPlayerId());
        if (target == null) {
            throw new BusinessException(404, "目标选手不存在");
        }
        if (target.getStatus() == null || !target.getStatus().equals(STATUS_ACTIVE)) {
            throw new BusinessException(400, "目标选手状态异常");
        }
        if (!captain.getTeamId().equals(target.getTeamId())) {
            throw new BusinessException(400, "只能给本队队员发工资");
        }

        Integer teamBalance = team.getPCoins() != null ? team.getPCoins() : 0;
        if (teamBalance < amount) {
            throw new BusinessException(400, "队伍P币不足");
        }

        Integer targetBalance = target.getDeposit() != null ? target.getDeposit() : 0;
        team.setPCoins(teamBalance - amount);
        target.setDeposit(targetBalance + amount);
        teamMapper.updateById(team);
        playerMapper.updateById(target);

        // 队伍支出流水
        String note = request.getReason() != null && !request.getReason().isBlank()
                ? "（" + request.getReason().trim() + "）" : "";
        PLedger teamLedger = new PLedger();
        teamLedger.setTeamId(team.getId());
        teamLedger.setType("captain_salary");
        teamLedger.setAmount(-amount);
        teamLedger.setReason("队长 " + captain.getName() + " 发工资给 " + target.getName() + note);
        teamLedger.setSource("captain_salary");
        teamLedger.setBalanceBefore(teamBalance);
        teamLedger.setBalanceAfter(team.getPCoins());
        teamLedger.setIsVoided(0);
        pLedgerMapper.insert(teamLedger);

        // 队员收入流水
        PlayerDepositLedger playerLedger = new PlayerDepositLedger();
        playerLedger.setPlayerId(target.getId());
        playerLedger.setType("salary");
        playerLedger.setAmount(amount);
        playerLedger.setReason("队长 " + captain.getName() + " 发放工资" + note);
        playerLedger.setBalanceBefore(targetBalance);
        playerLedger.setBalanceAfter(target.getDeposit());
        playerLedger.setSource("captain_salary");
        playerLedger.setOperator(captain.getName());
        playerLedger.setIsVoided(0);
        depositLedgerMapper.insert(playerLedger);
    }

    @Override
    public void updateTeamInfo(Long captainPlayerId, CaptainUpdateTeamInfoRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求参数不能为空");
        }
        Player captain = requireCaptain(captainPlayerId);
        Team team = teamMapper.selectById(captain.getTeamId());
        if (team == null) {
            throw new BusinessException(404, "所属队伍不存在");
        }
        if (request.getDescription() != null) {
            String desc = request.getDescription().trim();
            if (desc.length() > 500) {
                throw new BusinessException(400, "队伍简介不能超过500字");
            }
            team.setDescription(desc);
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            String name = request.getName().trim();
            if (name.length() > 50) {
                throw new BusinessException(400, "队名不能超过50字");
            }
            team.setName(name);
        }
        if (request.getState() != null && !request.getState().isBlank()) {
            String state = request.getState().trim();
            if (state.length() > 20) {
                throw new BusinessException(400, "简写不能超过20字");
            }
            // 同赛季简写不可重复
            Long conflict = teamMapper.selectCount(new LambdaQueryWrapper<Team>()
                    .eq(Team::getSeason, team.getSeason())
                    .eq(Team::getState, state)
                    .ne(Team::getId, team.getId())
                    .eq(Team::getDeleted, 0));
            if (conflict != null && conflict > 0) {
                throw new BusinessException(400, "当前赛季已存在简写「" + state + "」");
            }
            team.setState(state);
        }
        teamMapper.updateById(team);
    }

    @Override
    public String uploadTeamLogo(Long captainPlayerId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择文件");
        }
        Player captain = requireCaptain(captainPlayerId);
        Team team = teamMapper.selectById(captain.getTeamId());
        if (team == null) {
            throw new BusinessException(404, "所属队伍不存在");
        }
        String filename = "team_" + team.getId() + "_" + UUID.randomUUID() + resolveUploadExtension(file);
        String finalName;
        try {
            Path dir = Path.of(uploadBaseDir, "teams");
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target.toFile());
            Path finalFile = ImageCompressUtil.compressUploadedFile(target);
            finalName = finalFile.getFileName().toString();
        } catch (IOException e) {
            log.error("队伍图标上传失败", e);
            throw new BusinessException(500, "文件保存失败");
        }
        String url = uploadUrlPrefix + "/teams/" + finalName;
        team.setLogoUrl(url);
        teamMapper.updateById(team);
        return url;
    }

    @Override
    @Transactional
    public void depositToTeam(Long captainPlayerId, CaptainDepositToTeamRequest request) {
        if (request == null || request.getAmount() == null || request.getAmount() <= 0) {
            throw new BusinessException(400, "转入金额必须为正数");
        }
        Integer amount = request.getAmount();

        Player captain = playerMapper.selectByIdForUpdate(captainPlayerId);
        if (captain == null) {
            throw new BusinessException(404, "队长不存在");
        }
        requireCaptainRole(captain);
        if (captain.getStatus() == null || !captain.getStatus().equals(STATUS_ACTIVE)) {
            throw new BusinessException(400, "队长状态异常");
        }
        if (captain.getTeamId() == null) {
            throw new BusinessException(400, "队长尚未归属任何队伍");
        }
        Integer captainBalance = captain.getDeposit() != null ? captain.getDeposit() : 0;
        if (captainBalance < amount) {
            throw new BusinessException(400, "队长个人积分不足");
        }
        Team team = teamMapper.selectByIdForUpdate(captain.getTeamId());
        if (team == null) {
            throw new BusinessException(404, "所属队伍不存在");
        }
        Integer teamBalance = team.getPCoins() != null ? team.getPCoins() : 0;

        captain.setDeposit(captainBalance - amount);
        team.setPCoins(teamBalance + amount);
        playerMapper.updateById(captain);
        teamMapper.updateById(team);

        String note = request.getReason() != null && !request.getReason().isBlank()
                ? "（" + request.getReason().trim() + "）" : "";

        // 队长个人支出流水
        PlayerDepositLedger playerLedger = new PlayerDepositLedger();
        playerLedger.setPlayerId(captain.getId());
        playerLedger.setType("transfer_out");
        playerLedger.setAmount(-amount);
        playerLedger.setReason("队长转入队伍资金" + note);
        playerLedger.setBalanceBefore(captainBalance);
        playerLedger.setBalanceAfter(captain.getDeposit());
        playerLedger.setSource("captain_deposit");
        playerLedger.setOperator(captain.getName());
        playerLedger.setIsVoided(0);
        depositLedgerMapper.insert(playerLedger);

        // 队伍收入流水
        PLedger teamLedger = new PLedger();
        teamLedger.setTeamId(team.getId());
        teamLedger.setType("captain_deposit");
        teamLedger.setAmount(amount);
        teamLedger.setReason("队长 " + captain.getName() + " 转入资金" + note);
        teamLedger.setSource("captain_deposit");
        teamLedger.setBalanceBefore(teamBalance);
        teamLedger.setBalanceAfter(team.getPCoins());
        teamLedger.setIsVoided(0);
        pLedgerMapper.insert(teamLedger);
    }

    private Player requireCaptain(Long playerId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BusinessException(404, "选手不存在");
        }
        requireCaptainRole(player);
        if (player.getTeamId() == null) {
            throw new BusinessException(400, "队长尚未归属任何队伍");
        }
        return player;
    }

    private void requireCaptainRole(Player player) {
        // 位掩码：role 含队长位（2）即可，兼容管理员+队长（3）
        if (player.getRole() == null || (player.getRole() & ROLE_CAPTAIN) == 0) {
            throw new BusinessException(403, "仅队长可访问");
        }
    }

    private CaptainContextVO.TeamMemberVO toMemberVO(Player p) {
        CaptainContextVO.TeamMemberVO m = new CaptainContextVO.TeamMemberVO();
        m.setId(p.getId());
        m.setName(p.getName());
        m.setPosition(p.getPosition());
        m.setValue(p.getValue());
        m.setDeposit(p.getDeposit());
        m.setIsSubstitute(p.getIsSubstitute());
        m.setIsCaptain(p.getRole() != null && p.getRole().equals(ROLE_CAPTAIN) ? 1 : 0);
        return m;
    }

    private CaptainPLedgerVO toPLedgerVO(PLedger row) {
        CaptainPLedgerVO vo = new CaptainPLedgerVO();
        vo.setId(row.getId());
        vo.setMatchId(row.getMatchId());
        vo.setType(row.getType());
        vo.setAmount(row.getAmount());
        vo.setReason(row.getReason());
        vo.setVersion(row.getVersion());
        vo.setBalanceBefore(row.getBalanceBefore());
        vo.setBalanceAfter(row.getBalanceAfter());
        vo.setIsVoided(row.getIsVoided());
        vo.setCreatedAt(row.getCreatedAt() != null ? row.getCreatedAt().toString().replace('T', ' ') : null);
        return vo;
    }

    private static String resolveUploadExtension(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            String ext = original.substring(original.lastIndexOf('.')).toLowerCase();
            if (".png".equals(ext)) {
                return ".png";
            }
            if (".jpg".equals(ext) || ".jpeg".equals(ext)) {
                return ".jpg";
            }
        }
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("png")) {
            return ".png";
        }
        return ".jpg";
    }
}
