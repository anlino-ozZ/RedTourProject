package com.redtour.business.controller;

import com.redtour.business.annotation.RequireRole;
import com.redtour.business.common.ApiResponse;
import com.redtour.business.dto.AnalyticsVisitsResult;
import com.redtour.business.dto.HotQuestionResult;
import com.redtour.business.dto.HotSpotResult;
import com.redtour.business.dto.ProductClickResult;
import com.redtour.business.service.AnalyticsService;
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

import java.util.List;

/**
 * 运营分析接口（管理端）：/api/v1/admin/analytics/*。
 * 全部 @RequireRole，且按 scenicAreaId 隔离（super 可传 null 查全量）。
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequireRole({"super_admin", "admin"})
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /** 游览量按天聚合。 */
    @GetMapping("/visits")
    public ApiResponse<AnalyticsVisitsResult> visits(
            @RequestParam(required = false) Long scenicAreaId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ApiResponse.success(analyticsService.visits(scenicAreaId, startDate, endDate));
    }

    /** 热门景点（按打卡次数）。 */
    @GetMapping("/hot-spots")
    public ApiResponse<List<HotSpotResult>> hotSpots(
            @RequestParam(required = false) Long scenicAreaId) {
        return ApiResponse.success(analyticsService.hotSpots(scenicAreaId));
    }

    /** 热门问题 topN（来自 popular_question 表）。 */
    @GetMapping("/hot-questions")
    public ApiResponse<List<HotQuestionResult>> hotQuestions(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int topN) {
        return ApiResponse.success(analyticsService.hotQuestions(topN));
    }

    /** 产品点击排行。 */
    @GetMapping("/products")
    public ApiResponse<List<ProductClickResult>> products(
            @RequestParam(required = false) Long scenicAreaId) {
        return ApiResponse.success(analyticsService.products(scenicAreaId));
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
