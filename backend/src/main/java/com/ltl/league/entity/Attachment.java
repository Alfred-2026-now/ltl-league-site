package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("attachments")
public class Attachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long matchId;

    private Long gameId;

    private String type;

    private String label;

    private String url;

    private String filePath;

    private String uploadedBy;

    private String note;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
