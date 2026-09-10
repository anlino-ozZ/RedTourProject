package com.redtour.business.service;

import com.redtour.business.dto.SttResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 触摸屏语音识别服务。
 */
public interface SttService {

    /** 校验音频并转发给 AI 引擎识别。 */
    SttResult transcribe(MultipartFile audio);
}
