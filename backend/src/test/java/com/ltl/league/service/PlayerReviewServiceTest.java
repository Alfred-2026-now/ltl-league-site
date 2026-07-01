package com.ltl.league.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.PlayerDepositLedger;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerReviewServiceTest {

    private final PlayerMapper playerMapper = mock(PlayerMapper.class);
    private final TeamMapper teamMapper = mock(TeamMapper.class);
    private final PlayerDepositLedgerMapper ledgerMapper = mock(PlayerDepositLedgerMapper.class);
    private final PlayerReviewMapper reviewMapper = mock(PlayerReviewMapper.class);
    private final PlayerReviewRatingMapper ratingMapper = mock(PlayerReviewRatingMapper.class);
    private final PlayerReviewTipMapper tipMapper = mock(PlayerReviewTipMapper.class);
    private final PlayerReviewServiceImpl service = new PlayerReviewServiceImpl(
            playerMapper, teamMapper, ledgerMapper, reviewMapper, ratingMapper, tipMapper);

    @Test
    void createReviewAllowsMultipleReviewsForSamePlayerAndStoresPositions() {
        PlayerReviewDtos.CreateReviewRequest request = new PlayerReviewDtos.CreateReviewRequest();
        request.setPlayerId(2L);
        request.setContent("对线稳，团战开得好");
        request.setAnonymous(false);
        request.setPositions(List.of("TOP", "JUG"));

        Player target = player(2L, "被评选手", 300);
        Player author = player(1L, "点评人", 200);
        when(playerMapper.selectById(2L)).thenReturn(target);
        when(playerMapper.selectById(1L)).thenReturn(author);

        service.createReview(1L, request);
        service.createReview(1L, request);

        ArgumentCaptor<PlayerReview> captor = ArgumentCaptor.forClass(PlayerReview.class);
        verify(reviewMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals("TOP,JUG", captor.getAllValues().get(0).getPositions());
        assertEquals(0, captor.getAllValues().get(0).getAnonymous());
        assertEquals(2L, captor.getAllValues().get(0).getPlayerId());
        assertEquals(1L, captor.getAllValues().get(0).getAuthorPlayerId());
    }

    @Test
    void ratingSameReviewTwiceUpdatesExistingRatingAndPopularity() {
        PlayerReview review = review(10L, 2L, 99L);
        review.setRatingCount(1);
        review.setRatingSum(4);
        review.setTipTotal(50);
        PlayerReviewRating existing = new PlayerReviewRating();
        existing.setId(77L);
        existing.setReviewId(10L);
        existing.setRaterPlayerId(1L);
        existing.setScore(4);
        when(reviewMapper.selectById(10L)).thenReturn(review);
        when(ratingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        PlayerReviewDtos.RatingRequest request = new PlayerReviewDtos.RatingRequest();
        request.setScore(2);

        PlayerReviewDtos.ReviewVO result = service.rateReview(1L, 10L, request);

        verify(ratingMapper).updateById(existing);
        verify(ratingMapper, never()).insert(any());
        assertEquals(1, review.getRatingCount());
        assertEquals(2, review.getRatingSum());
        assertEquals(2.0, review.getConfidenceScore());
        assertEquals(50.0, review.getPopularityScore());
        assertEquals(2.0, result.getConfidenceScore());
        assertEquals(50.0, result.getPopularityScore());
    }

    @Test
    void tipReviewDeductsTipperDepositAndCreditsHalfToAuthor() {
        PlayerReview review = review(10L, 2L, 99L);
        review.setRatingCount(2);
        review.setRatingSum(8);
        review.setTipTotal(0);
        Player tipper = player(1L, "打赏人", 1200);
        Player author = player(99L, "点评作者", 30);
        when(reviewMapper.selectById(10L)).thenReturn(review);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(tipper);
        when(playerMapper.selectByIdForUpdate(99L)).thenReturn(author);

        PlayerReviewDtos.TipRequest request = new PlayerReviewDtos.TipRequest();
        request.setAmount(500);

        service.tipReview(1L, 10L, request);

        assertEquals(700, tipper.getDeposit());
        assertEquals(280, author.getDeposit());
        verify(playerMapper).updateById(tipper);
        verify(playerMapper).updateById(author);
        verify(playerMapper, never()).selectByIdForUpdate(2L);

        ArgumentCaptor<PlayerDepositLedger> ledgerCaptor = ArgumentCaptor.forClass(PlayerDepositLedger.class);
        verify(ledgerMapper, times(2)).insert(ledgerCaptor.capture());
        List<PlayerDepositLedger> ledgers = ledgerCaptor.getAllValues();
        assertEquals(1L, ledgers.get(0).getPlayerId());
        assertEquals(-500, ledgers.get(0).getAmount());
        assertEquals("review_tip", ledgers.get(0).getType());
        assertEquals("player_review", ledgers.get(0).getSource());
        assertEquals(99L, ledgers.get(1).getPlayerId());
        assertEquals(250, ledgers.get(1).getAmount());
        assertEquals("review_tip_reward", ledgers.get(1).getType());
        assertEquals("player_review", ledgers.get(1).getSource());

        ArgumentCaptor<PlayerReviewTip> tipCaptor = ArgumentCaptor.forClass(PlayerReviewTip.class);
        verify(tipMapper).insert(tipCaptor.capture());
        assertEquals(10L, tipCaptor.getValue().getReviewId());
        assertEquals(1L, tipCaptor.getValue().getTipperPlayerId());
        assertEquals(500, tipCaptor.getValue().getAmount());

        assertEquals(500, review.getTipTotal());
        assertEquals(140.0, review.getPopularityScore());
    }

    @Test
    void tipReviewRejectsInsufficientDeposit() {
        PlayerReview review = review(10L, 2L, 99L);
        Player tipper = player(1L, "穷哥", 20);
        when(reviewMapper.selectById(10L)).thenReturn(review);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(tipper);
        PlayerReviewDtos.TipRequest request = new PlayerReviewDtos.TipRequest();
        request.setAmount(50);

        BusinessException error = assertThrows(BusinessException.class, () -> service.tipReview(1L, 10L, request));

        assertEquals("选手积分不足，无法打赏", error.getMessage());
        verify(ledgerMapper, never()).insert(any());
        verify(tipMapper, never()).insert(any());
    }

    @Test
    void nonAdminCannotEditOrDeleteReview() {
        PlayerReviewDtos.UpdateReviewRequest request = new PlayerReviewDtos.UpdateReviewRequest();
        request.setContent("改一下");
        request.setPositions(List.of("MID"));
        request.setAnonymous(true);

        BusinessException editError = assertThrows(BusinessException.class,
                () -> service.updateReview(1L, 0, 10L, request));
        BusinessException deleteError = assertThrows(BusinessException.class,
                () -> service.deleteReview(1L, 0, 10L));

        assertEquals(403, editError.getCode());
        assertEquals(403, deleteError.getCode());
    }

    private Player player(Long id, String name, Integer deposit) {
        Player player = new Player();
        player.setId(id);
        player.setName(name);
        player.setDeposit(deposit);
        player.setStatus(1);
        player.setDeleted(0);
        return player;
    }

    private PlayerReview review(Long id, Long playerId, Long authorPlayerId) {
        PlayerReview review = new PlayerReview();
        review.setId(id);
        review.setPlayerId(playerId);
        review.setAuthorPlayerId(authorPlayerId);
        review.setContent("点评内容");
        review.setPositions("TOP");
        review.setAnonymous(0);
        review.setRatingCount(0);
        review.setRatingSum(0);
        review.setTipTotal(0);
        review.setConfidenceScore(0.0);
        review.setPopularityScore(0.0);
        review.setDeleted(0);
        return review;
    }
}
