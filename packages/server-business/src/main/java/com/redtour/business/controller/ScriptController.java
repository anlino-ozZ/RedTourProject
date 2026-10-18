package com.redtour.business.controller;

import com.redtour.business.annotation.RequireRole;
import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.PageResult;
import com.redtour.business.dto.ScriptRequest;
import com.redtour.business.dto.ScriptResult;
import com.redtour.business.service.ScriptService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 剧本管理端接口（增删改查 + 上下架，带景区归属隔离），位于 /api/v1/admin/scripts。
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/scripts")
@RequireRole({"super_admin", "admin"})
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptService scriptService;

    @GetMapping
    public ApiResponse<PageResult<ScriptResult>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须为正整数") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须为正整数")
            @Max(value = 100, message = "每页最多查询100条") int pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long scenicAreaId,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(scriptService.list(page, pageSize, title, scenicAreaId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<ScriptResult> get(@PathVariable @Positive(message = "剧本ID必须为正整数") Long id) {
        return ApiResponse.success(scriptService.get(id));
    }

    @PostMapping
    public ApiResponse<ScriptResult> create(@Valid @RequestBody ScriptRequest request) {
        return ApiResponse.success(scriptService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ScriptResult> update(
            @PathVariable @Positive(message = "剧本ID必须为正整数") Long id,
            @Valid @RequestBody ScriptRequest request) {
        return ApiResponse.success(scriptService.update(id, request));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<ScriptResult> updateStatus(
            @PathVariable @Positive(message = "剧本ID必须为正整数") Long id,
            @RequestParam @Min(value = 0, message = "状态值不合法") @Max(value = 1, message = "状态值不合法") Integer status) {
        return ApiResponse.success(scriptService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive(message = "剧本ID必须为正整数") Long id) {
        scriptService.delete(id);
        return ApiResponse.success(null);
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
