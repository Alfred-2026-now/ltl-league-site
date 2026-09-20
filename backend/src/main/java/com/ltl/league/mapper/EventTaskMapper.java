package com.ltl.league.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltl.league.entity.EventTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EventTaskMapper extends BaseMapper<EventTask> {
    @Select("SELECT * FROM event_tasks WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    EventTask selectByIdForUpdate(@Param("id") Long id);
}
