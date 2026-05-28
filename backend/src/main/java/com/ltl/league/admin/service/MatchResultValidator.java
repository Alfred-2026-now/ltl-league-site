package com.ltl.league.admin.service;

import com.ltl.league.admin.dto.GameDraftDTO;
import com.ltl.league.admin.dto.MatchResultDraftRequest;
import com.ltl.league.entity.Team;
import com.ltl.league.exception.BusinessException;

import java.util.*;
public final class MatchResultValidator {

    private MatchResultValidator() {
    }

    public static void validateDraft(String format, boolean bo5Enabled, MatchResultDraftRequest req, Team home, Team away) {
        if (req == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String fmt = format != null ? format.toUpperCase() : "";
        String resultType = normalizeResultType(req.getResultType());

        int homeScore = req.getHomeScore() != null ? req.getHomeScore() : -1;
        int awayScore = req.getAwayScore() != null ? req.getAwayScore() : -1;

        if ("forfeit".equals(resultType)) {
            if (req.getWinnerTeamId() == null) {
                throw new BusinessException(400, "弃赛必须指定胜方队伍");
            }
            homeScore = req.getHomeScore() != null ? req.getHomeScore() : -1;
            awayScore = req.getAwayScore() != null ? req.getAwayScore() : -1;
        }

        if (homeScore < 0 || awayScore < 0) {
            throw new BusinessException(400, "主客队比分不能为空");
        }

        validateScorePair(fmt, homeScore, awayScore, bo5Enabled);

        Long winnerId = req.getWinnerTeamId();
        validateWinner(fmt, homeScore, awayScore, winnerId, home.getId(), away.getId());

        if (req.getHomePoints() == null || req.getAwayPoints() == null) {
            throw new BusinessException(400, "本场积分必须填写");
        }

        if (req.getGames() != null && !req.getGames().isEmpty()) {
            validateGames(fmt, homeScore, awayScore, home.getState(), away.getState(), req.getGames());
        }
    }

    public static void validateForPublish(String format, boolean bo5Enabled, MatchResultDraftRequest req, Team home, Team away) {
        validateDraft(format, bo5Enabled, req, home, away);
    }

    private static String normalizeResultType(String type) {
        if (type == null || type.isBlank()) return "normal";
        return type.trim().toLowerCase();
    }

    private static void validateScorePair(String format, int home, int away, boolean bo5Enabled) {
        Set<String> allowed = allowedScores(format, bo5Enabled);
        String key = home + ":" + away;
        if (!allowed.contains(key)) {
            throw new BusinessException(400, "比分不合法: " + home + ":" + away + "（赛制 " + format + "）");
        }
    }

    private static Set<String> allowedScores(String format, boolean bo5Enabled) {
        Set<String> s = new HashSet<>();
        switch (format) {
            case "BO1" -> {
                s.add("1:0");
                s.add("0:1");
            }
            case "BO2" -> {
                s.add("2:0");
                s.add("1:1");
                s.add("0:2");
            }
            case "BO3" -> {
                s.add("2:0");
                s.add("2:1");
                s.add("1:2");
                s.add("0:2");
            }
            case "BO5" -> {
                if (!bo5Enabled) {
                    throw new BusinessException(400, "BO5 赛制未开放");
                }
                s.add("3:0");
                s.add("3:1");
                s.add("3:2");
                s.add("2:3");
                s.add("1:3");
                s.add("0:3");
            }
            default -> throw new BusinessException(400, "未知赛制: " + format);
        }
        return s;
    }

    private static void validateWinner(String format, int home, int away, Long winnerId, Long homeId, Long awayId) {
        if ("BO2".equals(format) && home == 1 && away == 1) {
            if (winnerId != null) {
                throw new BusinessException(400, "BO2 平局时胜方必须为空");
            }
            return;
        }
        if (home == away) {
            throw new BusinessException(400, "非平局比分不能相同");
        }
        if (winnerId == null) {
            throw new BusinessException(400, "必须指定胜方");
        }
        if (home > away && !winnerId.equals(homeId)) {
            throw new BusinessException(400, "胜方必须是主队");
        }
        if (away > home && !winnerId.equals(awayId)) {
            throw new BusinessException(400, "胜方必须是客队");
        }
    }

    private static void validateGames(String format, int homeScore, int awayScore,
                                      String homeState, String awayState, List<GameDraftDTO> games) {
        int maxGames = maxGames(format);
        if (games.size() > maxGames) {
            throw new BusinessException(400, "小局数量超过赛制上限: " + maxGames);
        }

        Set<Integer> indexes = new HashSet<>();
        int homeWins = 0;
        int awayWins = 0;

        int playedGames = homeScore + awayScore;

        for (GameDraftDTO g : games) {
            if (g.getGameIndex() == null) {
                throw new BusinessException(400, "小局序号不能为空");
            }
            if (g.getGameIndex() < 1 || g.getGameIndex() > playedGames) {
                throw new BusinessException(400, "小局序号须在 1～" + playedGames + " 之间（与总比分局数一致）");
            }
            if (!indexes.add(g.getGameIndex())) {
                throw new BusinessException(400, "小局序号重复: " + g.getGameIndex());
            }
            if (g.getWinner() == null || g.getWinner().isBlank()) {
                throw new BusinessException(400, "小局胜方不能为空");
            }
            String winner = g.getWinner().trim();
            if (!winner.equals(homeState) && !winner.equals(awayState)) {
                throw new BusinessException(400, "小局胜方必须是主队或客队简称");
            }
            if (winner.equals(homeState)) homeWins++;
            else awayWins++;

            String blue = g.getBlueTeam() != null ? g.getBlueTeam().trim() : "";
            String red = g.getRedTeam() != null ? g.getRedTeam().trim() : "";
            if (blue.isEmpty() || red.isEmpty()) {
                throw new BusinessException(400, "蓝方和红方不能为空");
            }
            if (blue.equals(red)) {
                throw new BusinessException(400, "蓝方和红方不能相同");
            }
            Set<String> teams = Set.of(homeState, awayState);
            if (!teams.contains(blue) || !teams.contains(red)) {
                throw new BusinessException(400, "蓝方和红方只能是主队或客队");
            }
        }

        if (homeWins != homeScore || awayWins != awayScore) {
            throw new BusinessException(400, "小局胜负数量与总比分不一致");
        }

        if ("BO2".equals(format) && homeScore == 1 && awayScore == 1) {
            if (games.size() != 2) {
                throw new BusinessException(400, "BO2 平局必须录入两局小局");
            }
            if (homeWins != 1 || awayWins != 1) {
                throw new BusinessException(400, "BO2 平局必须双方各赢一局");
            }
        }
    }

    private static int maxGames(String format) {
        return switch (format) {
            case "BO1" -> 1;
            case "BO2" -> 2;
            case "BO3" -> 3;
            case "BO5" -> 5;
            default -> 0;
        };
    }

}
