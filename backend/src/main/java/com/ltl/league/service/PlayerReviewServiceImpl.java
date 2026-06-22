package com.ltl.league.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.PlayerDepositLedger;
import com.ltl.league.entity.Team;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.PlayerDepositLedgerMapper;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.mapper.TeamMapper;
import com.ltl.league.dto.PlayerReviewDtos;
import com.ltl.league.entity.PlayerReview;
import com.ltl.league.entity.PlayerReviewRating;
import com.ltl.league.entity.PlayerReviewTip;
import com.ltl.league.mapper.PlayerReviewMapper;
import com.ltl.league.mapper.PlayerReviewRatingMapper;
import com.ltl.league.mapper.PlayerReviewTipMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlayerReviewServiceImpl {

    private static final Set<String> ALLOWED_POSITIONS = Set.of("TOP", "JUG", "MID", "BOT", "SUP");
    private static final Set<Integer> ALLOWED_TIP_AMOUNTS = Set.of(10, 50, 100);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PlayerMapper playerMapper;
    private final TeamMapper teamMapper;
    private final PlayerDepositLedgerMapper ledgerMapper;
    private final PlayerReviewMapper reviewMapper;
    private final PlayerReviewRatingMapper ratingMapper;
    private final PlayerReviewTipMapper tipMapper;

    public PlayerReviewServiceImpl(
            PlayerMapper playerMapper,
            TeamMapper teamMapper,
            PlayerDepositLedgerMapper ledgerMapper,
            PlayerReviewMapper reviewMapper,
            PlayerReviewRatingMapper ratingMapper,
            PlayerReviewTipMapper tipMapper) {
        this.playerMapper = playerMapper;
        this.teamMapper = teamMapper;
        this.ledgerMapper = ledgerMapper;
        this.reviewMapper = reviewMapper;
        this.ratingMapper = ratingMapper;
        this.tipMapper = tipMapper;
    }

    public PlayerReviewDtos.PlayerListResponse listPlayers(String sort, Long currentUserId, Integer role) {
        List<Player> players = playerMapper.selectList(new LambdaQueryWrapper<Player>()
                .eq(Player::getDeleted, 0));
        List<Team> teams = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                .eq(Team::getDeleted, 0));
        List<PlayerReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<PlayerReview>()
                .eq(PlayerReview::getDeleted, 0));

        Map<Long, Team> teamMap = teams.stream().collect(Collectors.toMap(Team::getId, Function.identity()));
        Map<Long, List<PlayerReview>> reviewMap = reviews.stream()
                .collect(Collectors.groupingBy(PlayerReview::getPlayerId));

        List<PlayerReviewDtos.PlayerSummaryVO> summaries = players.stream()
                .map(player -> toPlayerSummary(player, teamMap.get(player.getTeamId()),
                        reviewMap.getOrDefault(player.getId(), List.of())))
                .collect(Collectors.toCollection(ArrayList::new));

        Comparator<PlayerReviewDtos.PlayerSummaryVO> comparator;
        if ("popularity".equals(sort)) {
            comparator = Comparator
                    .comparing((PlayerReviewDtos.PlayerSummaryVO vo) -> safeDouble(vo.getTotalPopularity())).reversed()
                    .thenComparing(vo -> safeInt(vo.getReviewCount()), Comparator.reverseOrder());
        } else {
            comparator = Comparator
                    .comparing((PlayerReviewDtos.PlayerSummaryVO vo) -> safeInt(vo.getReviewCount())).reversed()
                    .thenComparing(vo -> safeDouble(vo.getTotalPopularity()), Comparator.reverseOrder());
        }
        summaries.sort(comparator.thenComparing(PlayerReviewDtos.PlayerSummaryVO::getPlayerId));

        PlayerReviewDtos.PlayerListResponse response = new PlayerReviewDtos.PlayerListResponse();
        response.setCurrentUserId(currentUserId);
        response.setAdmin(isAdmin(role));
        response.setPlayers(summaries);
        return response;
    }

    public PlayerReviewDtos.PlayerDetailResponse getPlayerReviews(Long playerId, Long currentUserId, Integer role) {
        Player player = requirePlayer(playerId);
        Team team = player.getTeamId() == null ? null : teamMapper.selectById(player.getTeamId());
        List<PlayerReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<PlayerReview>()
                .eq(PlayerReview::getDeleted, 0)
                .eq(PlayerReview::getPlayerId, playerId));
        reviews.sort(Comparator
                .comparing((PlayerReview review) -> safeDouble(review.getPopularityScore())).reversed()
                .thenComparing(PlayerReview::getId, Comparator.reverseOrder()));

        Map<Long, Player> authors = loadPlayersByIds(reviews.stream()
                .map(PlayerReview::getAuthorPlayerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, Integer> currentRatings = loadCurrentRatings(reviews, currentUserId);

        PlayerReviewDtos.PlayerDetailResponse response = new PlayerReviewDtos.PlayerDetailResponse();
        response.setCurrentUserId(currentUserId);
        response.setAdmin(isAdmin(role));
        response.setPlayer(toPlayerSummary(player, team, reviews));
        response.setReviews(reviews.stream()
                .map(review -> toReviewVO(review, player, authors.get(review.getAuthorPlayerId()),
                        currentRatings.get(review.getId()), role))
                .collect(Collectors.toList()));
        return response;
    }

    @Transactional
    public PlayerReviewDtos.ReviewVO createReview(Long actorPlayerId, PlayerReviewDtos.CreateReviewRequest request) {
        if (request == null) {
            throw new BusinessException(400, "点评内容不能为空");
        }
        Player target = requirePlayer(request.getPlayerId());
        Player author = requirePlayer(actorPlayerId);

        PlayerReview review = new PlayerReview();
        review.setPlayerId(target.getId());
        review.setAuthorPlayerId(author.getId());
        review.setContent(normalizeContent(request.getContent()));
        review.setPositions(normalizePositions(request.getPositions()));
        review.setAnonymous(Boolean.TRUE.equals(request.getAnonymous()) ? 1 : 0);
        review.setRatingCount(0);
        review.setRatingSum(0);
        review.setConfidenceScore(0.0);
        review.setTipTotal(0);
        review.setPopularityScore(0.0);
        review.setDeleted(0);
        reviewMapper.insert(review);
        return toReviewVO(review, target, author, null, null);
    }

    @Transactional
    public PlayerReviewDtos.ReviewVO rateReview(Long actorPlayerId, Long reviewId, PlayerReviewDtos.RatingRequest request) {
        int score = normalizeScore(request);
        PlayerReview review = requireReview(reviewId);

        PlayerReviewRating existing = ratingMapper.selectOne(new LambdaQueryWrapper<PlayerReviewRating>()
                .eq(PlayerReviewRating::getDeleted, 0)
                .eq(PlayerReviewRating::getReviewId, reviewId)
                .eq(PlayerReviewRating::getRaterPlayerId, actorPlayerId));
        if (existing == null) {
            PlayerReviewRating rating = new PlayerReviewRating();
            rating.setReviewId(reviewId);
            rating.setRaterPlayerId(actorPlayerId);
            rating.setScore(score);
            rating.setDeleted(0);
            ratingMapper.insert(rating);
            review.setRatingCount(safeInt(review.getRatingCount()) + 1);
            review.setRatingSum(safeInt(review.getRatingSum()) + score);
        } else {
            int oldScore = safeInt(existing.getScore());
            existing.setScore(score);
            ratingMapper.updateById(existing);
            review.setRatingSum(safeInt(review.getRatingSum()) - oldScore + score);
        }

        recalculate(review);
        reviewMapper.updateById(review);
        return toReviewVO(review, null, null, score, null);
    }

    @Transactional
    public PlayerReviewDtos.ReviewVO tipReview(Long actorPlayerId, Long reviewId, PlayerReviewDtos.TipRequest request) {
        PlayerReview review = requireReview(reviewId);
        int amount = normalizeTipAmount(request);
        Player tipper = playerMapper.selectByIdForUpdate(actorPlayerId);
        if (tipper == null || safeInt(tipper.getDeleted()) != 0) {
            throw new BusinessException(401, "请先登录后再操作");
        }
        int before = safeInt(tipper.getDeposit());
        if (before < amount) {
            throw new BusinessException(400, "选手积分不足，无法打赏");
        }
        int after = before - amount;
        Player author = Objects.equals(review.getAuthorPlayerId(), actorPlayerId)
                ? tipper
                : playerMapper.selectByIdForUpdate(review.getAuthorPlayerId());
        if (author == null || safeInt(author.getDeleted()) != 0) {
            throw new BusinessException(404, "点评作者不存在");
        }

        PlayerDepositLedger ledger = new PlayerDepositLedger();
        ledger.setPlayerId(actorPlayerId);
        ledger.setType("review_tip");
        ledger.setAmount(-amount);
        ledger.setReason("点评打赏：reviewId=" + reviewId);
        ledger.setBalanceBefore(before);
        ledger.setBalanceAfter(after);
        ledger.setSource("player_review");
        ledger.setOperator(tipper.getName());
        ledger.setIsVoided(0);
        ledger.setDeleted(0);
        ledgerMapper.insert(ledger);

        tipper.setDeposit(after);
        playerMapper.updateById(tipper);

        int reward = amount / 2;
        int authorBefore = safeInt(author.getDeposit());
        int authorAfter = authorBefore + reward;
        PlayerDepositLedger rewardLedger = new PlayerDepositLedger();
        rewardLedger.setPlayerId(author.getId());
        rewardLedger.setType("review_tip_reward");
        rewardLedger.setAmount(reward);
        rewardLedger.setReason("点评打赏分成：reviewId=" + reviewId);
        rewardLedger.setBalanceBefore(authorBefore);
        rewardLedger.setBalanceAfter(authorAfter);
        rewardLedger.setSource("player_review");
        rewardLedger.setOperator(tipper.getName());
        rewardLedger.setIsVoided(0);
        rewardLedger.setDeleted(0);
        ledgerMapper.insert(rewardLedger);

        author.setDeposit(authorAfter);
        playerMapper.updateById(author);

        PlayerReviewTip tip = new PlayerReviewTip();
        tip.setReviewId(reviewId);
        tip.setTipperPlayerId(actorPlayerId);
        tip.setAmount(amount);
        tip.setDepositLedgerId(ledger.getId());
        tip.setDeleted(0);
        tipMapper.insert(tip);

        review.setTipTotal(safeInt(review.getTipTotal()) + amount);
        recalculate(review);
        reviewMapper.updateById(review);
        return toReviewVO(review, null, null, null, null);
    }

    @Transactional
    public PlayerReviewDtos.ReviewVO updateReview(
            Long actorPlayerId,
            Integer role,
            Long reviewId,
            PlayerReviewDtos.UpdateReviewRequest request) {
        requireAdmin(role);
        if (request == null) {
            throw new BusinessException(400, "点评内容不能为空");
        }
        PlayerReview review = requireReview(reviewId);
        review.setContent(normalizeContent(request.getContent()));
        review.setPositions(normalizePositions(request.getPositions()));
        review.setAnonymous(Boolean.TRUE.equals(request.getAnonymous()) ? 1 : 0);
        recalculate(review);
        reviewMapper.updateById(review);
        return toReviewVO(review, null, null, null, role);
    }

    @Transactional
    public void deleteReview(Long actorPlayerId, Integer role, Long reviewId) {
        requireAdmin(role);
        PlayerReview review = requireReview(reviewId);
        review.setDeleted(1);
        reviewMapper.updateById(review);
    }

    private Player requirePlayer(Long playerId) {
        if (playerId == null) {
            throw new BusinessException(400, "选手不存在");
        }
        Player player = playerMapper.selectById(playerId);
        if (player == null || safeInt(player.getDeleted()) != 0) {
            throw new BusinessException(404, "选手不存在");
        }
        return player;
    }

    private PlayerReview requireReview(Long reviewId) {
        if (reviewId == null) {
            throw new BusinessException(404, "点评不存在");
        }
        PlayerReview review = reviewMapper.selectById(reviewId);
        if (review == null || safeInt(review.getDeleted()) != 0) {
            throw new BusinessException(404, "点评不存在");
        }
        return review;
    }

    private void requireAdmin(Integer role) {
        if (!isAdmin(role)) {
            throw new BusinessException(403, "仅管理员可编辑或删除点评");
        }
    }

    private boolean isAdmin(Integer role) {
        return Integer.valueOf(1).equals(role);
    }

    private String normalizeContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(400, "点评内容不能为空");
        }
        if (normalized.length() > 800) {
            throw new BusinessException(400, "点评内容不能超过800字");
        }
        return normalized;
    }

    private String normalizePositions(List<String> positions) {
        if (positions == null || positions.isEmpty()) {
            throw new BusinessException(400, "请选择擅长位置");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String position : positions) {
            String code = position == null ? "" : position.trim().toUpperCase();
            if (!ALLOWED_POSITIONS.contains(code)) {
                throw new BusinessException(400, "位置参数不正确");
            }
            normalized.add(code);
        }
        return String.join(",", normalized);
    }

    private int normalizeScore(PlayerReviewDtos.RatingRequest request) {
        if (request == null || request.getScore() == null) {
            throw new BusinessException(400, "评分不能为空");
        }
        int score = request.getScore();
        if (score < 0 || score > 5) {
            throw new BusinessException(400, "评分必须在0-5之间");
        }
        return score;
    }

    private int normalizeTipAmount(PlayerReviewDtos.TipRequest request) {
        if (request == null || request.getAmount() == null || !ALLOWED_TIP_AMOUNTS.contains(request.getAmount())) {
            throw new BusinessException(400, "打赏金额必须为10P、50P或100P");
        }
        return request.getAmount();
    }

    private void recalculate(PlayerReview review) {
        int ratingCount = safeInt(review.getRatingCount());
        int ratingSum = safeInt(review.getRatingSum());
        int tipTotal = safeInt(review.getTipTotal());
        double confidence = ratingCount == 0 ? 0.0 : ratingSum * 1.0 / ratingCount;
        double popularity = confidence * 20 + ratingCount * 5 + tipTotal / 10.0;
        review.setConfidenceScore(round3(confidence));
        review.setPopularityScore(round3(popularity));
    }

    private PlayerReviewDtos.PlayerSummaryVO toPlayerSummary(Player player, Team team, List<PlayerReview> reviews) {
        PlayerReviewDtos.PlayerSummaryVO vo = new PlayerReviewDtos.PlayerSummaryVO();
        vo.setPlayerId(player.getId());
        vo.setTeamId(player.getTeamId());
        vo.setPlayerName(player.getName());
        vo.setValue(player.getValue());
        vo.setDeposit(player.getDeposit());
        vo.setTeamState(team == null ? null : team.getState());
        vo.setTeamName(team == null ? null : team.getName());
        vo.setReviewCount(reviews.size());
        double totalPopularity = reviews.stream()
                .mapToDouble(review -> safeDouble(review.getPopularityScore()))
                .sum();
        PlayerReview top = reviews.stream()
                .max(Comparator.comparing((PlayerReview review) -> safeDouble(review.getPopularityScore()))
                        .thenComparing(PlayerReview::getId))
                .orElse(null);
        vo.setTopPopularity(top == null ? 0.0 : safeDouble(top.getPopularityScore()));
        vo.setTotalPopularity(round3(totalPopularity));
        vo.setTopReviewSnippet(top == null ? "" : snippet(top.getContent()));
        return vo;
    }

    private PlayerReviewDtos.ReviewVO toReviewVO(
            PlayerReview review,
            Player target,
            Player author,
            Integer currentUserRating,
            Integer role) {
        PlayerReviewDtos.ReviewVO vo = new PlayerReviewDtos.ReviewVO();
        vo.setId(review.getId());
        vo.setPlayerId(review.getPlayerId());
        vo.setPlayerName(target == null ? null : target.getName());
        boolean anonymous = Integer.valueOf(1).equals(review.getAnonymous());
        vo.setAnonymous(anonymous);
        vo.setAuthorDisplayName(anonymous ? "匿名" : (author == null ? null : author.getName()));
        vo.setPositions(splitPositions(review.getPositions()));
        vo.setContent(review.getContent());
        vo.setRatingCount(safeInt(review.getRatingCount()));
        vo.setRatingSum(safeInt(review.getRatingSum()));
        vo.setConfidenceScore(safeDouble(review.getConfidenceScore()));
        vo.setTipTotal(safeInt(review.getTipTotal()));
        vo.setPopularityScore(safeDouble(review.getPopularityScore()));
        vo.setCurrentUserRating(currentUserRating);
        vo.setCanAdmin(isAdmin(role));
        vo.setCreatedAt(review.getCreatedAt() == null ? null : review.getCreatedAt().format(DATE_TIME_FORMATTER));
        return vo;
    }

    private Map<Long, Player> loadPlayersByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return playerMapper.selectList(new LambdaQueryWrapper<Player>()
                        .eq(Player::getDeleted, 0)
                        .in(Player::getId, ids))
                .stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));
    }

    private Map<Long, Integer> loadCurrentRatings(List<PlayerReview> reviews, Long currentUserId) {
        if (currentUserId == null || reviews.isEmpty()) {
            return Map.of();
        }
        List<Long> reviewIds = reviews.stream().map(PlayerReview::getId).collect(Collectors.toList());
        return ratingMapper.selectList(new LambdaQueryWrapper<PlayerReviewRating>()
                        .eq(PlayerReviewRating::getDeleted, 0)
                        .eq(PlayerReviewRating::getRaterPlayerId, currentUserId)
                        .in(PlayerReviewRating::getReviewId, reviewIds))
                .stream()
                .collect(Collectors.toMap(PlayerReviewRating::getReviewId, PlayerReviewRating::getScore));
    }

    private List<String> splitPositions(String positions) {
        if (positions == null || positions.isBlank()) {
            return List.of();
        }
        return List.of(positions.split(","));
    }

    private String snippet(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 48 ? content : content.substring(0, 48) + "...";
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
