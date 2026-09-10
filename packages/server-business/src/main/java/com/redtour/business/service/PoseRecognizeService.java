package com.redtour.business.service;

import com.redtour.business.dto.PoseRecognizeRequest;
import com.redtour.business.dto.PoseRecognizeResult;

/**
 * 姿态识别 HTTP 代理服务。
 */
public interface PoseRecognizeService {

    /** 调用 AI 引擎识别 Base64 图像帧。 */
    PoseRecognizeResult recognize(PoseRecognizeRequest request);
}
