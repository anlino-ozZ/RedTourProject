package com.redtour.business.controller;

import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.PageResult;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.ProfileResult;
import com.redtour.business.dto.SpotFavoriteResult;
import com.redtour.business.dto.VisitRecordResult;
import com.redtour.business.service.ProfileService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心接口（需登录，无 @RequireRole，由 AuthInterceptor 校验 JWT）。
 */
@Validated
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ApiResponse<ProfileResult> getProfile() {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(profileService.getProfile(userId));
    }

    @GetMapping("/records")
    public ApiResponse<PageResult<VisitRecordResult>> listRecords(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须为正整数") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须为正整数")
            @Max(value = 100, message = "每页最多查询100条") int pageSize) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(profileService.listRecords(userId, page, pageSize));
    }

    @GetMapping("/favorites")
    public ApiResponse<PageResult<SpotFavoriteResult>> listFavorites(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须为正整数") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须为正整数")
            @Max(value = 100, message = "每页最多查询100条") int pageSize) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(profileService.listFavorites(userId, page, pageSize));
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
