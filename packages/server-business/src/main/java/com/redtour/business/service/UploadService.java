package com.redtour.business.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件上传服务（管理端 /admin/uploads）。
 */
public interface UploadService {

    /** 保存上传文件，返回可访问 URL 列表。 */
    List<String> upload(MultipartFile[] files, String type, Long scenicAreaId);

    /** 按 URL 删除已上传文件。 */
    void deleteFiles(List<String> urls);
}
