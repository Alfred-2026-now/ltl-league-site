package com.ltl.league.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltl.league.entity.Attachment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AttachmentMapper extends BaseMapper<Attachment> {

    @Delete("DELETE FROM attachments WHERE result_id = #{resultId} AND type = 'score_screenshot'")
    int physicalDeleteScreenshotsByResultId(@Param("resultId") Long resultId);

    @Delete("DELETE FROM attachments WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /** 含已逻辑删除的行，重建小局前必须执行 */
    @Update("UPDATE attachments SET game_id = NULL WHERE result_id = #{resultId} AND type = 'score_screenshot'")
    int detachAllScreenshotGameLinks(@Param("resultId") Long resultId);
}
