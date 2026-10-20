package com.redtour.business.controller;

import com.redtour.business.annotation.RequireRole;
import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.PageResult;
import com.redtour.business.dto.CheckpointRequest;
import com.redtour.business.dto.CheckpointResult;
import com.redtour.business.service.CheckpointService;
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
 * 打卡点管理端接口（仅管理端，无公开浏览端），位于 /api/v1/admin/checkpoints。
 * checkpoint 表无 scenic_area_id，景区归属通过 spot 间接确定，隔离以"spot 所属景区"为准。
 * 该前缀不在 WebMvcConfig 白名单内，故自动走 JWT 拦截器鉴权（@RequireRole）。
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/checkpoints")
@RequireRole({"super_admin", "admin"})
@RequiredArgsConstructor
public class CheckpointController {

    private final CheckpointService checkpointService;

    @GetMapping
    public ApiResponse<PageResult<CheckpointResult>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须为正整数") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须为正整数")
            @Max(value = 100, message = "每页最多查询100条") int pageSize,
            @RequestParam(required = false) Long scenicAreaId,
            @RequestParam(required = false) Long spotId) {
        return ApiResponse.success(checkpointService.list(page, pageSize, scenicAreaId, spotId));
    }

    @GetMapping("/{id}")
    public ApiResponse<CheckpointResult> get(@PathVariable @Positive(message = "打卡点ID必须为正整数") Long id) {
        return ApiResponse.success(checkpointService.get(id));
    }

    @PostMapping
    public ApiResponse<CheckpointResult> create(@Valid @RequestBody CheckpointRequest request) {
        return ApiResponse.success(checkpointService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CheckpointResult> update(
            @PathVariable @Positive(message = "打卡点ID必须为正整数") Long id,
            @Valid @RequestBody CheckpointRequest request) {
        return ApiResponse.success(checkpointService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive(message = "打卡点ID必须为正整数") Long id) {
        checkpointService.delete(id);
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
