package com.redtour.business.service.impl;

import com.redtour.business.common.PageResult;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.ArtifactRequest;
import com.redtour.business.dto.ArtifactResult;
import com.redtour.business.entity.Artifact;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.ArtifactMapper;
import com.redtour.business.service.ArtifactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 文物管理服务实现：景区归属隔离 + 分页 + 下线（status=0）。
 * 字段以 schema.sql / Artifact 实体为准（intro / images / era / location / category / wikiRef）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtifactServiceImpl implements ArtifactService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ArtifactMapper artifactMapper;

    @Override
    public PageResult<ArtifactResult> list(int page, int pageSize, String name, Long scenicAreaId, Integer status) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        Long scopedAreaId = scopeScenicAreaId(scenicAreaId);
        String normalizedName = StringUtils.hasText(name) ? name.trim() : null;
        List<Artifact> rows = artifactMapper.findPage(scopedAreaId, normalizedName, status, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<ArtifactResult> data = rows.stream().map(this::toResult).toList();
        return PageResult.of(data, artifactMapper.count(scopedAreaId, normalizedName, status), page, pageSize);
    }

    @Override
    public ArtifactResult get(Long id) {
        Artifact artifact = loadArtifact(id);
        authorizeArtifact(artifact.getScenicAreaId());
        return toResult(artifact);
    }

    @Override
    public ArtifactResult create(ArtifactRequest request) {
        SysUser current = UserContext.getCurrentUser();
        Long areaId = resolveCreateScenicAreaId(current, request.getScenicAreaId());
        Artifact artifact = new Artifact();
        artifact.setScenicAreaId(areaId);
        artifact.setName(request.getName().trim());
        artifact.setEra(blankToNull(request.getEra()));
        artifact.setIntro(request.getIntro() == null ? null : request.getIntro().trim());
        artifact.setImages(blankToNull(request.getImages()));
        artifact.setLocation(blankToNull(request.getLocation()));
        artifact.setCategory(blankToNull(request.getCategory()));
        artifact.setWikiRef(blankToNull(request.getWikiRef()));
        artifact.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        artifactMapper.insert(artifact);
        if (artifact.getId() == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文物创建失败");
        }
        return toResult(artifact);
    }

    @Override
    public ArtifactResult update(Long id, ArtifactRequest request) {
        Artifact artifact = loadArtifact(id);
        authorizeArtifact(artifact.getScenicAreaId());
        if (request.getName() != null) {
            artifact.setName(request.getName().trim());
        }
        if (request.getEra() != null) {
            artifact.setEra(blankToNull(request.getEra()));
        }
        if (request.getIntro() != null) {
            artifact.setIntro(request.getIntro().trim());
        }
        if (request.getImages() != null) {
            artifact.setImages(blankToNull(request.getImages()));
        }
        if (request.getLocation() != null) {
            artifact.setLocation(blankToNull(request.getLocation()));
        }
        if (request.getCategory() != null) {
            artifact.setCategory(blankToNull(request.getCategory()));
        }
        if (request.getWikiRef() != null) {
            artifact.setWikiRef(blankToNull(request.getWikiRef()));
        }
        if (request.getStatus() != null) {
            if (request.getStatus() != 0 && request.getStatus() != 1) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "状态值不合法");
            }
            artifact.setStatus(request.getStatus());
        }
        artifactMapper.updateById(artifact);
        return toResult(artifact);
    }

    @Override
    public void delete(Long id) {
        Artifact artifact = loadArtifact(id);
        authorizeArtifact(artifact.getScenicAreaId());
        artifactMapper.delete(id);
    }

    @Override
    public ArtifactResult updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "状态值不合法");
        }
        Artifact artifact = loadArtifact(id);
        authorizeArtifact(artifact.getScenicAreaId());
        artifactMapper.updateStatus(id, status);
        artifact.setStatus(status);
        return toResult(artifact);
    }

    @Override
    public PageResult<ArtifactResult> publicList(int page, int pageSize, Long scenicAreaId, String keyword) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        String normalizedName = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<Artifact> rows = artifactMapper.findPublicPage(scenicAreaId, normalizedName, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<ArtifactResult> data = rows.stream().map(this::toResult).toList();
        return PageResult.of(data, artifactMapper.countPublic(scenicAreaId, normalizedName), page, pageSize);
    }

    @Override
    public ArtifactResult publicGet(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文物ID必须为正整数");
        }
        Artifact artifact = artifactMapper.findPublicById(id);
        if (artifact == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文物不存在或已下架");
        }
        return toResult(artifact);
    }

    private Artifact loadArtifact(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文物ID必须为正整数");
        }
        Artifact artifact = artifactMapper.findById(id);
        if (artifact == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文物不存在");
        }
        return artifact;
    }

    private ArtifactResult toResult(Artifact artifact) {
        ArtifactResult result = new ArtifactResult();
        result.setId(artifact.getId());
        result.setScenicAreaId(artifact.getScenicAreaId());
        result.setName(artifact.getName());
        result.setEra(artifact.getEra());
        result.setIntro(artifact.getIntro());
        result.setImages(artifact.getImages());
        result.setLocation(artifact.getLocation());
        result.setCategory(artifact.getCategory());
        result.setWikiRef(artifact.getWikiRef());
        result.setStatus(artifact.getStatus());
        result.setCreatedAt(artifact.getCreatedAt());
        result.setUpdatedAt(artifact.getUpdatedAt());
        return result;
    }

    /** admin 强制本人景区；super 透传筛选条件。 */
    private Long scopeScenicAreaId(Long scenicAreaId) {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if ("super_admin".equals(current.getRole())) {
            return scenicAreaId;
        }
        if (!"admin".equals(current.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问文物数据");
        }
        if (current.getScenicAreaId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前管理员未分配景区");
        }
        return current.getScenicAreaId();
    }

    /** 新增时的归属景区解析：admin 强制本人景区；super 须指定。 */
    private Long resolveCreateScenicAreaId(SysUser current, Long requestedAreaId) {
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if ("super_admin".equals(current.getRole())) {
            if (requestedAreaId == null || requestedAreaId <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "超级管理员创建文物须指定归属景区");
            }
            return requestedAreaId;
        }
        if (!"admin".equals(current.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权创建文物");
        }
        if (current.getScenicAreaId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前管理员未分配景区");
        }
        return current.getScenicAreaId();
    }

    private void authorizeArtifact(Long scenicAreaId) {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if ("super_admin".equals(current.getRole())) {
            return;
        }
        if (!"admin".equals(current.getRole())
                || current.getScenicAreaId() == null
                || !current.getScenicAreaId().equals(scenicAreaId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他景区的数据");
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private void validatePage(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页参数不合法");
        }
    }
}
