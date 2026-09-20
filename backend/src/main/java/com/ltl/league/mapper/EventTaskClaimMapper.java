package com.ltl.league.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltl.league.entity.EventTaskClaim;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EventTaskClaimMapper extends BaseMapper<EventTaskClaim> {
    @Select("SELECT * FROM event_task_claims WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    EventTaskClaim selectByIdForUpdate(@Param("id") Long id);
}
