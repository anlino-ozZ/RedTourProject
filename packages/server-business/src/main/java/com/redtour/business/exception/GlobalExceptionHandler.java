package com.redtour.business.exception;

import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理，统一返回 ApiResponse
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("[全局异常] {}", e.getMessage(), e);
        return ApiResponse.error(ResultCode.INTERNAL_ERROR);
    }
}
