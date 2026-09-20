package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("event_task_proof_images")
public class EventTaskProofImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long proofId;
    private Long taskId;
    private Long claimId;
    private String label;
    private String url;
    private String filePath;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
