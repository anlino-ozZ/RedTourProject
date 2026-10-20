package com.redtour.business.controller;

import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.PageResult;
import com.redtour.business.dto.SpotResult;
import com.redtour.business.service.SpotService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * 景点游客端接口（公开只读，仅返回上架景点）。
 */
@Validated
@RestController
@RequestMapping("/api/v1/spots")
@RequiredArgsConstructor
public class SpotPublicController {

    private final SpotService spotService;

    @GetMapping
    public ApiResponse<PageResult<SpotResult>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须为正整数") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须为正整数")
            @Max(value = 100, message = "每页最多查询100条") int pageSize,
            @RequestParam(required = false) Long scenicAreaId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(spotService.publicList(page, pageSize, scenicAreaId, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<SpotResult> get(@PathVariable @Positive(message = "景点ID必须为正整数") Long id) {
        return ApiResponse.success(spotService.publicGet(id));
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
