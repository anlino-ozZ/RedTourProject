package com.redtour.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 智能问答请求（透传 AI 引擎）
 */
@Data
public class AskRequest {

    /** 问题内容 */
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过500个字符")
    private String question;

    /** 景区 ID */
    @Positive(message = "景区ID必须为正整数")
    private Long scenicAreaId;

    /** 是否语音播报 */
    private Boolean useVoice = false;
}
