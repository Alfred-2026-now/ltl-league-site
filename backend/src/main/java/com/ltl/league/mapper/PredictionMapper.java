package com.ltl.league.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltl.league.entity.Prediction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PredictionMapper extends BaseMapper<Prediction> {
    @Select("SELECT * FROM match_predictions WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Prediction selectByIdForUpdate(@Param("id") Long id);

    /** 撤回结算：清空结算快照并回到 PUBLISHED（updateById 不写 NULL 字段，故用显式 SQL） */
    @Update("UPDATE match_predictions SET status = 'PUBLISHED', correct_option_id = NULL, winner_count = 0, " +
            "reward_p_per_winner = 0, reward_bounty_per_winner = 0, settled_at = NULL, settled_by_player_id = NULL, " +
            "updated_at = NOW() WHERE id = #{id} AND deleted = 0")
    int clearSettlement(@Param("id") Long id);
}
