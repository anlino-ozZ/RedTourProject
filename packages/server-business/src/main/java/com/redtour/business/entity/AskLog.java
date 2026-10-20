package com.redtour.business.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答记录实体，对应 ask_log 表。
 */
@Data
public class AskLog {

    private Long id;
    private Long scenicAreaId;
    /** 游客用户 ID（触摸屏端为 null） */
    private Long userId;
    private String question;
    private String answer;
    /** JSON 字符串，由业务层统一序列化 Wiki 引用列表。 */
    private String sources;
    private Long durationMs;
    private String deviceId;
    /** 客户端生成的去重 ID（离线同步使用） */
    private String clientId;
    private LocalDateTime createdAt;
}
