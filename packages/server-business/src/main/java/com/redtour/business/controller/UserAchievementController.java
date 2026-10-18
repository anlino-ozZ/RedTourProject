package com.redtour.business.controller;

import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.UserAchievementResult;
import com.redtour.business.service.UserAchievementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户成就公开接口（需登录，无 @RequireRole，由 AuthInterceptor 校验 JWT）。
 * 返回当前登录用户已解锁的成就列表。
 */
@RestController
@RequestMapping("/api/v1/achievements")
@RequiredArgsConstructor
public class UserAchievementController {

    private final UserAchievementService userAchievementService;

    @GetMapping
    public ApiResponse<List<UserAchievementResult>> listCurrentUserAchievements() {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(userAchievementService.listCurrentUserAchievements(userId));
    }
}
