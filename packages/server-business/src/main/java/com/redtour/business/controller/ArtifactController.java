package com.redtour.business.controller;

import com.redtour.business.annotation.RequireRole;
import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.PageResult;
import com.redtour.business.dto.ArtifactRequest;
import com.redtour.business.dto.ArtifactResult;
import com.redtour.business.service.ArtifactService;
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
 * 文物管理端接口（增删改查 + 上下架），位于 /api/v1/admin/artifacts。
 * 该前缀不在 WebMvcConfig 白名单内，故自动走 JWT 拦截器鉴权（@RequireRole）。
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/artifacts")
@RequireRole({"super_admin", "admin"})
@RequiredArgsConstructor
public class ArtifactController {

    private final ArtifactService artifactService;

    @GetMapping
    public ApiResponse<PageResult<ArtifactResult>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须为正整数") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须为正整数")
            @Max(value = 100, message = "每页最多查询100条") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long scenicAreaId,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(artifactService.list(page, pageSize, name, scenicAreaId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<ArtifactResult> get(@PathVariable @Positive(message = "文物ID必须为正整数") Long id) {
        return ApiResponse.success(artifactService.get(id));
    }

    @PostMapping
    public ApiResponse<ArtifactResult> create(@Valid @RequestBody ArtifactRequest request) {
        return ApiResponse.success(artifactService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ArtifactResult> update(
            @PathVariable @Positive(message = "文物ID必须为正整数") Long id,
            @Valid @RequestBody ArtifactRequest request) {
        return ApiResponse.success(artifactService.update(id, request));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<ArtifactResult> updateStatus(
            @PathVariable @Positive(message = "文物ID必须为正整数") Long id,
            @RequestParam @Min(value = 0, message = "状态值不合法") @Max(value = 1, message = "状态值不合法") Integer status) {
        return ApiResponse.success(artifactService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive(message = "文物ID必须为正整数") Long id) {
        artifactService.delete(id);
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
