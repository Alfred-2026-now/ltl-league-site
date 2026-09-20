package com.ltl.league.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltl.league.entity.EventTaskProof;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EventTaskProofMapper extends BaseMapper<EventTaskProof> {
    @Select("SELECT * FROM event_task_proofs WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    EventTaskProof selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM event_task_proofs WHERE claim_id = #{claimId} AND status = 'APPROVED' AND deleted = 0 ORDER BY id DESC LIMIT 1 FOR UPDATE")
    EventTaskProof selectApprovedByClaimForUpdate(@Param("claimId") Long claimId);
}
