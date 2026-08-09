package com.redtour.business.common;

import lombok.Getter;

/**
 * 全局错误码
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "ok"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    INTERNAL_ERROR(500, "服务异常"),
    AI_ENGINE_ERROR(5001, "AI 引擎调用失败"),
    HARDWARE_ERROR(5002, "硬件通信失败"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
