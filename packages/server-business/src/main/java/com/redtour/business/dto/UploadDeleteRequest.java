package com.redtour.business.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量删除上传文件请求：传入待删除文件 URL 列表。
 */
@Data
public class UploadDeleteRequest {

    @NotEmpty(message = "urls 不能为空")
    private List<String> urls;
}
