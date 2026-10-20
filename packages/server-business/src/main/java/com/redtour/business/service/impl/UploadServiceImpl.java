package com.redtour.business.service.impl;

import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.service.UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文件上传服务实现：
 * - 保存到 {data.upload.path}/{type}/{scenicAreaId}/{uuid}{ext}
 * - 按类型做大小校验
 * - 删除时把 /uploads/... URL 映射回物理路径并防穿越删除
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private static final long MB = 1024L * 1024L;
    private static final String ALLOWED_TYPES = "image,audio,wiki,icon";

    @Value("${data.upload.path}")
    private String uploadPath;

    @Value("${data.upload.max-image-mb:10}")
    private long maxImageMb;

    @Value("${data.upload.max-audio-mb:50}")
    private long maxAudioMb;

    @Value("${data.upload.max-wiki-mb:20}")
    private long maxWikiMb;

    /** URL 前缀（与静态资源映射 /uploads/** 对应）。 */
    private static final String URL_PREFIX = "/uploads/";

    @Override
    public List<String> upload(MultipartFile[] files, String type, Long scenicAreaId) {
        if (files == null || files.length == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上传文件不能为空");
        }
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "非法的文件类型: " + type);
        }
        Long areaId = resolveScenicAreaId(scenicAreaId);
        long maxBytes = maxBytesFor(type);

        Path base = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path dir = base.resolve(type).resolve(String.valueOf(areaId));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("[上传] 创建目录失败 {}", dir, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件存储目录创建失败");
        }

        List<String> urls = new ArrayList<>(files.length);
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (file.getSize() > maxBytes) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "文件 " + file.getOriginalFilename() + " 超过大小限制（" + (maxBytes / MB) + "MB）");
            }
            String ext = extractExt(file.getOriginalFilename());
            String filename = UUID.randomUUID().toString() + ext;
            Path target = dir.resolve(filename);
            try {
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.error("[上传] 保存文件失败 {}", target, e);
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件保存失败");
            }
            urls.add(URL_PREFIX + type + "/" + areaId + "/" + filename);
        }
        return urls;
    }

    @Override
    public void deleteFiles(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        Path base = Paths.get(uploadPath).toAbsolutePath().normalize();
        for (String url : urls) {
            if (url == null || !url.startsWith(URL_PREFIX)) {
                log.warn("[上传] 跳过非法删除 URL: {}", url);
                continue;
            }
            String relative = url.substring(URL_PREFIX.length());
            Path target = base.resolve(relative).normalize();
            // 防穿越：目标必须仍在上传根目录内
            if (!target.startsWith(base)) {
                log.warn("[上传] 拒绝越权删除路径: {}", url);
                continue;
            }
            try {
                Files.deleteIfExists(target);
            } catch (IOException e) {
                log.warn("[上传] 删除文件失败 {}", target, e);
            }
        }
    }

    private long maxBytesFor(String type) {
        return switch (type) {
            case "image", "icon" -> maxImageMb * MB;
            case "audio" -> maxAudioMb * MB;
            case "wiki" -> maxWikiMb * MB;
            default -> maxImageMb * MB;
        };
    }

    private String extractExt(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int idx = originalFilename.lastIndexOf('.');
        if (idx < 0 || idx == originalFilename.length() - 1) {
            return "";
        }
        String ext = originalFilename.substring(idx).toLowerCase();
        // 仅保留 .字母数字，避免 ../ 之类注入
        if (!ext.matches("\\.[a-zA-Z0-9]+")) {
            return "";
        }
        return ext;
    }

    /** admin 强制本人景区；super 必须指定。 */
    private Long resolveScenicAreaId(Long requestedAreaId) {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if ("super_admin".equals(current.getRole())) {
            if (requestedAreaId == null || requestedAreaId <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "超级管理员上传文件须指定归属景区");
            }
            return requestedAreaId;
        }
        if (!"admin".equals(current.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (current.getScenicAreaId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前管理员未分配景区");
        }
        return current.getScenicAreaId();
    }
}
