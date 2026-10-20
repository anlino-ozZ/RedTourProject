package com.redtour.business.controller;

import com.redtour.business.annotation.RequireRole;
import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.PageResult;
import com.redtour.business.common.ResultCode;
import com.redtour.business.dto.ScenicAreaRequest;
import com.redtour.business.dto.ScenicAreaResult;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.service.ScenicAreaService;
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
 * 景区管理端接口（增删改查 + 上下架），位于 /api/v1/admin/scenic-areas。
 * 该前缀不在 WebMvcConfig 白名单内，故自动走 JWT 拦截器鉴权（@RequireRole）。
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/scenic-areas")
@RequireRole({"super_admin", "admin"})
@RequiredArgsConstructor
public class ScenicAreaController {

    private final ScenicAreaService scenicAreaService;

    @GetMapping
    public ApiResponse<PageResult<ScenicAreaResult>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须为正整数") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须为正整数")
            @Max(value = 100, message = "每页最多查询100条") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String region) {
        return ApiResponse.success(scenicAreaService.list(page, pageSize, name, status, region));
    }

    @GetMapping("/{id}")
    public ApiResponse<ScenicAreaResult> get(@PathVariable @Positive(message = "景区ID必须为正整数") Long id) {
        return ApiResponse.success(scenicAreaService.get(id));
    }

    @PostMapping
    public ApiResponse<ScenicAreaResult> create(@Valid @RequestBody ScenicAreaRequest request) {
        return ApiResponse.success(scenicAreaService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ScenicAreaResult> update(
            @PathVariable @Positive(message = "景区ID必须为正整数") Long id,
            @Valid @RequestBody ScenicAreaRequest request) {
        return ApiResponse.success(scenicAreaService.update(id, request));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<ScenicAreaResult> updateStatus(
            @PathVariable @Positive(message = "景区ID必须为正整数") Long id,
            @RequestParam @Min(value = 0, message = "状态值不合法") @Max(value = 1, message = "状态值不合法") Integer status) {
        return ApiResponse.success(scenicAreaService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive(message = "景区ID必须为正整数") Long id) {
        scenicAreaService.delete(id);
        return ApiResponse.success(null);
    }

    /** 将方法级参数校验失败保持为统一业务响应。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("参数格式错误");
        return ApiResponse.error(400, message);
    }
}
