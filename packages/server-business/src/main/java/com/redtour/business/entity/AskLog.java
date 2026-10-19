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
    private String question;
    private String answer;
    /** JSON 字符串，由业务层统一序列化 Wiki 引用列表。 */
    private String sources;
    private Long durationMs;
    private String deviceId;
    private LocalDateTime createdAt;
}
