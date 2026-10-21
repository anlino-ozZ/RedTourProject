package com.redtour.business.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 下发播放讲解音频请求（管理端 → 树莓派 play_audio 指令）。
 */
@Data
public class DeviceAudioPlayRequest {

    /**
     * 音频地址：
     * - 相对路径（如 /audio/spots/1.mp3、/audio/tts/xxx.wav）由树莓派拼业务后端根地址下载
     * - 完整 http(s) URL 由树莓派直接下载
     */
    @NotBlank(message = "音频地址不能为空")
    private String audioUrl;
}
