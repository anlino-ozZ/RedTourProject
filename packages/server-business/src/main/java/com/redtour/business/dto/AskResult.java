package com.redtour.business.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能问答结果，与 AI 引擎 /engine/ask 的 camelCase 字段保持一致。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AskResult {

    /** 原始问题 */
    private String question;

    /** AI 回答或降级提示 */
    private String answer;

    /** 引用的 Wiki 条目路径 */
    private List<String> sources = new ArrayList<>();

    /** AI 引擎处理耗时（毫秒） */
    private Long durationMs = 0L;

    /** TTS 音频地址；未请求或生成失败时为 null */
    private String audioUrl;
}
