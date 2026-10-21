package com.redtour.business.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 下发音量设置请求（管理端 → 树莓派 set_volume 指令）。
 */
@Data
public class DeviceVolumeRequest {

    /** 音量 0-100 */
    @NotNull(message = "音量不能为空")
    @Min(value = 0, message = "音量需在 0-100 之间")
    @Max(value = 100, message = "音量需在 0-100 之间")
    private Integer volume;
}
