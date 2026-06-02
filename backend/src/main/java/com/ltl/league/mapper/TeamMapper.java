package com.ltl.league.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltl.league.entity.Team;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TeamMapper extends BaseMapper<Team> {

    @Select("SELECT * FROM teams WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Team selectByIdForUpdate(@Param("id") Long id);
}
