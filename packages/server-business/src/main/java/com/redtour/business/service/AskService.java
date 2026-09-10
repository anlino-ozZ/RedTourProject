package com.redtour.business.service;

import com.redtour.business.dto.AskRequest;
import com.redtour.business.dto.AskResult;

/**
 * 智能问答业务服务。
 */
public interface AskService {

    /** 调用 AI 引擎并记录问答日志。 */
    AskResult ask(AskRequest request, String deviceId);
}
