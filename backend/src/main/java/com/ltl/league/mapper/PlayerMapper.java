package com.ltl.league.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltl.league.entity.Player;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PlayerMapper extends BaseMapper<Player> {

    @Select("SELECT * FROM players WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Player selectByIdForUpdate(@Param("id") Long id);

    @Select({
            "<script>",
            "SELECT * FROM players WHERE deleted = 0 AND id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "ORDER BY id FOR UPDATE",
            "</script>"
    })
    List<Player> selectByIdsForUpdate(@Param("ids") List<Long> ids);
}
