package com.redtour.business.controller;

import com.redtour.business.annotation.RequireRole;
import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.PageResult;
import com.redtour.business.dto.AchievementRequest;
import com.redtour.business.dto.AchievementResult;
import com.redtour.business.service.AchievementService;
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
 * 成就定义管理端接口（增删改查），位于 /api/v1/admin/achievements。
 * 全局模块，无 scenic_area_id，不做景区隔离；管理端仍受 @RequireRole(super_admin, admin) 保护。
 * 公开端「当前用户成就」由子代理B 实现，本控制器不提供 /api/v1/achievements。
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/achievements")
@RequireRole({"super_admin", "admin"})
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    @GetMapping
    public ApiResponse<PageResult<AchievementResult>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须为正整数") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须为正整数")
            @Max(value = 100, message = "每页最多查询100条") int pageSize,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long scenicAreaId) {
        return ApiResponse.success(achievementService.list(page, pageSize, triggerType, name, scenicAreaId));
    }

    @GetMapping("/{id}")
    public ApiResponse<AchievementResult> get(@PathVariable @Positive(message = "成就ID必须为正整数") Long id) {
        return ApiResponse.success(achievementService.get(id));
    }

    @PostMapping
    public ApiResponse<AchievementResult> create(@Valid @RequestBody AchievementRequest request) {
        return ApiResponse.success(achievementService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AchievementResult> update(
            @PathVariable @Positive(message = "成就ID必须为正整数") Long id,
            @Valid @RequestBody AchievementRequest request) {
        return ApiResponse.success(achievementService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive(message = "成就ID必须为正整数") Long id) {
        achievementService.delete(id);
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
