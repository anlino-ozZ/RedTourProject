package com.redtour.business.controller;

import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.PageResult;
import com.redtour.business.common.ResultCode;
import com.redtour.business.dto.WikiEntryResult;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.service.WikiEntryService;
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
 * Wiki 公开读取接口（触摸屏快捷信息页数据源）。
 *
 * 与 /api/v1/admin/wiki 的区别：
 * - 本接口在鉴权白名单内，无登录概念，按 scenicAreaId 过滤，且只返回「已发布版本」：
 *   compile_status = 'done' 返回当前内容，pending/compiling/failed 回退到上次编译成功的快照，
 *   从未编译成功的条目不返回（避免游客读到半成品）；
 * - /api/v1/admin/wiki 是管理端接口，走 JWT + @RequireRole，普管只能访问自己景区，
 *   且必须能看到 pending/compiling/failed 才能重编，故不做编译状态过滤。
 * 早先两者共用一条路径，导致去掉 token 就能匿名读取任意景区 Wiki 正文，故此处拆开。
 */
@Validated
@RestController
@RequestMapping("/api/v1/wiki")
@RequiredArgsConstructor
public class WikiPublicController {

    private final WikiEntryService wikiEntryService;

    @GetMapping
    public ApiResponse<PageResult<WikiEntryResult>> list(
            @RequestParam("scenicAreaId") @Positive(message = "景区ID必须为正整数") Long scenicAreaId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须为正整数") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须为正整数")
            @Max(value = 100, message = "每页最多查询100条") int pageSize,
            @RequestParam(required = false) String keyword) {
        if (scenicAreaId == null || scenicAreaId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "景区ID必须为正整数");
        }
        return ApiResponse.success(wikiEntryService.publicList(scenicAreaId, page, pageSize, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<WikiEntryResult> get(@PathVariable @Positive(message = "Wiki条目ID必须为正整数") Long id) {
        return ApiResponse.success(wikiEntryService.publicGet(id));
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
