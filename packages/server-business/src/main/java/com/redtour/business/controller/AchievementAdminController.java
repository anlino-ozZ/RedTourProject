package com.redtour.business.controller;

import com.redtour.business.annotation.RequireRole;
import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.AchievementUsersResult;
import com.redtour.business.service.UserAchievementService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成就管理端补充接口（需登录 + @RequireRole）。
 *
 * 说明：成就的增删改查（list/get/create/update/delete）由子代理A 在 /api/v1/admin/achievements
 * 的 AchievementController 中实现；此处仅补充「某成就解锁概况」子接口，避免类名冲突。
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/achievements")
@RequireRole({"super_admin", "admin"})
@RequiredArgsConstructor
public class AchievementAdminController {

    private final UserAchievementService userAchievementService;

    @GetMapping("/{id}/users")
    public ApiResponse<AchievementUsersResult> listAchievementUsers(
            @PathVariable @Positive(message = "成就ID必须为正整数") Long id,
            @RequestParam(required = false) Long scenicAreaId) {
        // 当前用户已通过 @RequireRole 校验，service 内按角色解析 scenicAreaId 隔离。
        UserContext.getCurrentUser();
        return ApiResponse.success(userAchievementService.listAchievementUsers(id, scenicAreaId));
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
