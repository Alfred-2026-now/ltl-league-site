package com.ltl.league.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("players")
public class Player {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long teamId;

    private String name;

    private Integer value;

    private Integer topValue;

    private Integer jugValue;

    private Integer midValue;

    private Integer botValue;

    private Integer supValue;

    private Integer topActive;

    private Integer jugActive;

    private Integer midActive;

    private Integer botActive;

    private Integer supActive;

    private Integer maxValue;

    private String position;

    private String gameAccount;

    private String puuid;

    private Integer isSubstitute;

    private Integer isLoan;

    private Long loanTeamId;

    private Integer status;

    private Integer deposit;

    private Integer bounty;

    private Integer role;

    /** @deprecated 旧的"整人"衰减计时，已改为按位置独立计时（见下方 topDecayAt 等），保留仅为兼容。 */
    @Deprecated
    private java.time.LocalDateTime nextDecayAt;

    /** @deprecated 旧的"整人"衰减次数，已改为按位置独立计数（见下方 topDecayCount 等），保留仅为兼容。 */
    @Deprecated
    private Integer decayCount;

    // ===== 按位置独立计时的衰减（未参赛衰减用）=====
    // 每个"已激活"的位置各自一个计时器；null 表示该位置尚未启动计时（不参与衰减）。
    private java.time.LocalDateTime topDecayAt;
    private java.time.LocalDateTime jugDecayAt;
    private java.time.LocalDateTime midDecayAt;
    private java.time.LocalDateTime botDecayAt;
    private java.time.LocalDateTime supDecayAt;

    /** 各位置已发生的衰减次数（用于判断处于首阶段还是后续阶段） */
    private Integer topDecayCount;
    private Integer jugDecayCount;
    private Integer midDecayCount;
    private Integer botDecayCount;
    private Integer supDecayCount;

    /** 取某位置的下次衰减日期 */
    public java.time.LocalDateTime decayAtFor(String position) {
        switch (position == null ? "" : position.toUpperCase()) {
            case "TOP": return topDecayAt;
            case "JUG": return jugDecayAt;
            case "MID": return midDecayAt;
            case "BOT": return botDecayAt;
            case "SUP": return supDecayAt;
            default: return null;
        }
    }

    /** 设置某位置的下次衰减日期 */
    public void setDecayAtFor(String position, java.time.LocalDateTime value) {
        switch (position == null ? "" : position.toUpperCase()) {
            case "TOP": this.topDecayAt = value; break;
            case "JUG": this.jugDecayAt = value; break;
            case "MID": this.midDecayAt = value; break;
            case "BOT": this.botDecayAt = value; break;
            case "SUP": this.supDecayAt = value; break;
            default: break;
        }
    }

    /** 取某位置已发生的衰减次数（缺省 0） */
    public int decayCountFor(String position) {
        Integer v;
        switch (position == null ? "" : position.toUpperCase()) {
            case "TOP": v = topDecayCount; break;
            case "JUG": v = jugDecayCount; break;
            case "MID": v = midDecayCount; break;
            case "BOT": v = botDecayCount; break;
            case "SUP": v = supDecayCount; break;
            default: v = null;
        }
        return v != null ? v : 0;
    }

    /** 设置某位置已发生的衰减次数 */
    public void setDecayCountFor(String position, Integer value) {
        switch (position == null ? "" : position.toUpperCase()) {
            case "TOP": this.topDecayCount = value; break;
            case "JUG": this.jugDecayCount = value; break;
            case "MID": this.midDecayCount = value; break;
            case "BOT": this.botDecayCount = value; break;
            case "SUP": this.supDecayCount = value; break;
            default: break;
        }
    }

    /** 取某位置的激活标记（1=已激活） */
    public Integer activeFor(String position) {
        switch (position == null ? "" : position.toUpperCase()) {
            case "TOP": return topActive;
            case "JUG": return jugActive;
            case "MID": return midActive;
            case "BOT": return botActive;
            case "SUP": return supActive;
            default: return null;
        }
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
