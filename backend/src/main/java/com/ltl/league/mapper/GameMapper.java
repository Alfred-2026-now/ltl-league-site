package com.ltl.league.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltl.league.entity.Game;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GameMapper extends BaseMapper<Game> {

    /** 物理删除，避免逻辑删除后 uk_match_game 仍冲突 */
    @Delete("DELETE FROM games WHERE result_id = #{resultId}")
    int physicalDeleteByResultId(@Param("resultId") Long resultId);

    @Delete("DELETE FROM games WHERE match_id = #{matchId}")
    int physicalDeleteByMatchId(@Param("matchId") Long matchId);
}
