package com.redtour.business.controller;

import com.redtour.business.annotation.RequireRole;
import com.redtour.business.common.ApiResponse;
import com.redtour.business.dto.UploadDeleteRequest;
import com.redtour.business.service.UploadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件上传接口（管理端）：/api/v1/admin/uploads。
 * POST 多文件上传（按类型+景区落盘），DELETE 批量按 URL 删除。
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/uploads")
@RequireRole({"super_admin", "admin"})
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<String>> upload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam String type,
            @RequestParam @NotNull(message = "景区ID不能为空") Long scenicAreaId) {
        return ApiResponse.success(uploadService.upload(files, type, scenicAreaId));
    }

    @DeleteMapping
    public ApiResponse<Void> delete(@Valid @RequestBody UploadDeleteRequest request) {
        uploadService.deleteFiles(request.getUrls());
        return ApiResponse.success(null);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ApiResponse<Void> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException e) {
        return ApiResponse.error(400, "不支持的请求方法: " + e.getMethod());
    }
}
