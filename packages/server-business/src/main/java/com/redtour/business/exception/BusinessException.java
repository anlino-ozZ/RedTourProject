package com.redtour.business.exception;

import com.redtour.business.common.ResultCode;
import lombok.Getter;

/**
 * 自定义业务异常
 * 由 GlobalExceptionHandler 捕获并返回对应错误码给前端
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 错误码 */
    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode, String customMessage) {
        super(customMessage);
        this.code = resultCode.getCode();
    }
}
