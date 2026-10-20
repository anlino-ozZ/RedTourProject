package com.redtour.business.controller;

import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.PageResult;
import com.redtour.business.dto.ScriptCompleteResult;
import com.redtour.business.dto.ScriptResult;
import com.redtour.business.service.ScriptService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 剧本游客端接口（公开只读，仅返回上架剧本），位于 /api/v1/scripts（已加入 WebMvcConfig 白名单）。
 */
@Validated
@RestController
@RequestMapping("/api/v1/scripts")
@RequiredArgsConstructor
public class ScriptPublicController {

    private final ScriptService scriptService;

    @GetMapping
    public ApiResponse<PageResult<ScriptResult>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须为正整数") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须为正整数")
            @Max(value = 100, message = "每页最多查询100条") int pageSize,
            @RequestParam @Positive(message = "景区ID必须为正整数") Long scenicAreaId) {
        return ApiResponse.success(scriptService.publicList(page, pageSize, scenicAreaId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ScriptResult> get(@PathVariable @Positive(message = "剧本ID必须为正整数") Long id) {
        return ApiResponse.success(scriptService.publicGet(id));
    }

    /** 标记完成某剧本（需登录，走 JWT 鉴权；多段路径不在白名单内）。触发成就解锁判定。 */
    @PostMapping("/{id}/complete")
    public ApiResponse<ScriptCompleteResult> complete(
            @PathVariable @Positive(message = "剧本ID必须为正整数") Long id) {
        return ApiResponse.success(scriptService.complete(id));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("参数格式错误");
        return ApiResponse.error(400, message);
    }
}
