package com.redtour.business.dto;

import lombok.Data;

/**
 * 智能问答请求（透传 AI 引擎）
 */
@Data
public class AskRequest {

    /** 问题内容 */
    private String question;

    /** 景区 ID */
    private Integer scenicAreaId;

    /** 是否语音播报 */
    private Boolean useVoice;
}
