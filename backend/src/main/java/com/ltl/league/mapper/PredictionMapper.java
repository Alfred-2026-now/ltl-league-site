package com.ltl.league.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltl.league.entity.Prediction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PredictionMapper extends BaseMapper<Prediction> {
    @Select("SELECT * FROM match_predictions WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Prediction selectByIdForUpdate(@Param("id") Long id);
}
