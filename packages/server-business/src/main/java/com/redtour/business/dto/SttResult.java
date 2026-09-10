package com.redtour.business.dto;

import lombok.Data;

/**
 * 本地语音识别结果。
 */
@Data
public class SttResult {

    private String text;
    private Long durationMs;
}
