package com.redtour.business.exception;

import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.nio.file.AccessDeniedException;
import java.util.stream.Collectors;

/**
 * 全局异常处理：统一返回 ApiResponse 结构
 * 按细分异常捕获，精确映射错误码和 message，方便前端定位问题
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** ========== 业务异常（精确抛出，优先级最高） ========== */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException e) {
        log.warn("[业务异常] code={} message={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /** ========== 参数校验异常（@Valid 注解触发） ========== */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] {}", msg);
        return ApiResponse.error(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    /** ========== 缺少必传 Query 参数 ========== */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException e) {
        String msg = "缺少必传参数: " + e.getParameterName();
        log.warn("[缺少参数] {}", msg);
        return ApiResponse.error(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    /** ========== 参数类型转换失败（如把字符串传给 number 类型路径变量） ========== */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ApiResponse<Void> handleTypeMismatch(Exception e) {
        log.warn("[参数类型错误] {}", e.getMessage());
        return ApiResponse.error(ResultCode.BAD_REQUEST.getCode(), "参数格式错误");
    }

    /** ========== HTTP 方法不支持（如 GET/POST 用错） ========== */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[方法不支持] {}", e.getMessage());
        return ApiResponse.error(ResultCode.BAD_REQUEST.getCode(), "不支持的请求方法: " + e.getMethod());
    }

    /** ========== 数据库唯一键冲突（如用户名重复） ========== */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResponse<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.error("[数据冲突] {}", e.getMessage());
        return ApiResponse.error(ResultCode.CONFLICT, "数据唯一性冲突");
    }

    /** ========== Spring 权限拒绝 ========== */
    @ExceptionHandler(AccessDeniedException.class)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("[权限拒绝] {}", e.getMessage());
        return ApiResponse.error(ResultCode.FORBIDDEN);
    }

    /** ========== 兜底：其他所有异常 → 500 ========== */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("[全局异常] message={}", e.getMessage(), e);
        return ApiResponse.error(ResultCode.INTERNAL_ERROR);
    }
}
