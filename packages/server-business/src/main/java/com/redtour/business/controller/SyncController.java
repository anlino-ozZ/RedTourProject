package com.redtour.business.controller;

import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.SyncRequest;
import com.redtour.business.dto.SyncResult;
import com.redtour.business.service.SyncService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 离线数据同步接口（需登录，不加 @RequireRole，由 AuthInterceptor 校验 JWT）。
 * 触摸屏/移动端断网期间采集的打卡与问答，联网后批量上报，按 clientId 去重。
 */
@Validated
@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @PostMapping
    public ApiResponse<SyncResult> sync(@Valid @RequestBody SyncRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(syncService.sync(request, userId));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("参数格式错误");
        return ApiResponse.error(400, message);
    }
}
