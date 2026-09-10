package com.redtour.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Base64 单帧姿态识别请求。
 */
@Data
public class PoseRecognizeRequest {

    @NotBlank(message = "frame 不能为空")
    @Size(max = 14_100_000, message = "Base64 图片不能超过 10MB")
    private String frame;

    @Positive(message = "景区 ID 必须为正整数")
    private Long scenicAreaId;
}
