package com.redtour.business.controller;

import com.redtour.business.annotation.RequireRole;
import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.PageResult;
import com.redtour.business.common.ResultCode;
import com.redtour.business.dto.WikiEntryResult;
import com.redtour.business.dto.WikiUpdateRequest;
import com.redtour.business.dto.WikiUploadRequest;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.service.WikiEntryService;
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
 * Wiki 管理端接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/wiki")
@RequireRole({"super_admin", "admin"})
@RequiredArgsConstructor
public class WikiEntryController {

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
        if (page <= 0 || pageSize <= 0 || pageSize > 100) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页参数不合法");
        }
        return ApiResponse.success(wikiEntryService.list(scenicAreaId, page, pageSize, keyword));
    }

    @PostMapping("/upload")
    public ApiResponse<WikiEntryResult> upload(@Valid @RequestBody WikiUploadRequest request) {
        return ApiResponse.success(wikiEntryService.upload(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<WikiEntryResult> get(@PathVariable @Positive(message = "Wiki条目ID必须为正整数") Long id) {
        return ApiResponse.success(wikiEntryService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<WikiEntryResult> update(
            @PathVariable @Positive(message = "Wiki条目ID必须为正整数") Long id,
            @Valid @RequestBody WikiUpdateRequest request) {
        return ApiResponse.success(wikiEntryService.update(id, request));
    }

    @PostMapping("/{id}/compile")
    public ApiResponse<WikiEntryResult> compile(
            @PathVariable @Positive(message = "Wiki条目ID必须为正整数") Long id) {
        return ApiResponse.success(wikiEntryService.compile(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive(message = "Wiki条目ID必须为正整数") Long id) {
        wikiEntryService.delete(id);
        return ApiResponse.success(null);
    }

    /** 将方法级参数校验失败保持为统一业务响应。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("参数格式错误");
        return ApiResponse.error(400, message);
    }
}
