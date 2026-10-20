package com.redtour.business.service.impl;

import com.redtour.business.common.PageResult;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.ScenicAreaRequest;
import com.redtour.business.dto.ScenicAreaResult;
import com.redtour.business.entity.ScenicArea;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.ScenicAreaMapper;
import com.redtour.business.service.ScenicAreaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 景区管理服务实现：权限隔离 + 分页 + 软删除 + 名称唯一性校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScenicAreaServiceImpl implements ScenicAreaService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ScenicAreaMapper scenicAreaMapper;

    @Override
    public PageResult<ScenicAreaResult> list(int page, int pageSize, String name, Integer status, String region) {
        validatePage(page, pageSize);
        long offsetValue = (long) (page - 1) * pageSize;
        if (offsetValue > Integer.MAX_VALUE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页范围过大");
        }
        int offset = (int) offsetValue;
        String normalizedName = StringUtils.hasText(name) ? name.trim() : null;
        String normalizedRegion = StringUtils.hasText(region) ? region.trim() : null;
        List<ScenicArea> rows = scenicAreaMapper.findPage(normalizedName, status, normalizedRegion, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<ScenicAreaResult> data = rows.stream().map(this::toResult).toList();
        return PageResult.of(data, scenicAreaMapper.count(normalizedName, status, normalizedRegion), page, pageSize);
    }

    @Override
    public ScenicAreaResult get(Long id) {
        ScenicArea area = loadArea(id);
        authorizeScenicArea(area.getId());
        return toResult(area);
    }

    @Override
    public ScenicAreaResult create(ScenicAreaRequest request) {
        authorizeSuperAdmin();
        if (StringUtils.hasText(request.getName())
                && scenicAreaMapper.countByName(request.getName().trim()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "景区名称已存在");
        }
        ScenicArea area = new ScenicArea();
        area.setName(request.getName().trim());
        area.setIntro(request.getIntro());
        area.setLongitude(request.getLongitude());
        area.setLatitude(request.getLatitude());
        area.setAddress(request.getAddress());
        area.setOpenHours(request.getOpenHours());
        area.setCoverImage(request.getCoverImage());
        area.setRegion(request.getRegion());
        area.setPhone(request.getPhone());
        area.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        area.setStatus(request.getStatus());
        area.setDeleted(0);
        scenicAreaMapper.insert(area);
        if (area.getId() == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "景区创建失败");
        }
        return toResult(area);
    }

    @Override
    public ScenicAreaResult update(Long id, ScenicAreaRequest request) {
        ScenicArea area = loadArea(id);
        authorizeScenicArea(area.getId());
        if (StringUtils.hasText(request.getName()) && !request.getName().trim().equals(area.getName())
                && scenicAreaMapper.countByNameExcludeId(request.getName().trim(), id) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "景区名称已存在");
        }
        if (request.getName() != null) {
            area.setName(request.getName().trim());
        }
        if (request.getIntro() != null) {
            area.setIntro(request.getIntro());
        }
        if (request.getLongitude() != null) {
            area.setLongitude(request.getLongitude());
        }
        if (request.getLatitude() != null) {
            area.setLatitude(request.getLatitude());
        }
        if (request.getAddress() != null) {
            area.setAddress(request.getAddress());
        }
        if (request.getOpenHours() != null) {
            area.setOpenHours(request.getOpenHours());
        }
        if (request.getCoverImage() != null) {
            area.setCoverImage(request.getCoverImage());
        }
        if (request.getRegion() != null) {
            area.setRegion(request.getRegion());
        }
        if (request.getPhone() != null) {
            area.setPhone(request.getPhone());
        }
        if (request.getSortOrder() != null) {
            area.setSortOrder(request.getSortOrder());
        }
        if (request.getStatus() != null) {
            area.setStatus(request.getStatus());
        }
        scenicAreaMapper.updateById(area);
        return toResult(area);
    }

    @Override
    public void delete(Long id) {
        ScenicArea area = loadArea(id);
        authorizeScenicArea(area.getId());
        scenicAreaMapper.softDelete(id);
    }

    @Override
    public ScenicAreaResult updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "状态值不合法");
        }
        ScenicArea area = loadArea(id);
        authorizeScenicArea(area.getId());
        scenicAreaMapper.updateStatus(id, status);
        area.setStatus(status);
        return toResult(area);
    }

    @Override
    public PageResult<ScenicAreaResult> publicList(int page, int pageSize, String keyword) {
        validatePage(page, pageSize);
        long offsetValue = (long) (page - 1) * pageSize;
        int offset = (int) offsetValue;
        String normalizedName = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<ScenicArea> rows = scenicAreaMapper.findPublicPage(normalizedName, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<ScenicAreaResult> data = rows.stream().map(this::toResult).toList();
        return PageResult.of(data, scenicAreaMapper.countPublic(normalizedName), page, pageSize);
    }

    @Override
    public ScenicAreaResult publicGet(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "景区ID必须为正整数");
        }
        ScenicArea area = scenicAreaMapper.findPublicById(id);
        if (area == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "景区不存在或已下架");
        }
        return toResult(area);
    }

    private ScenicArea loadArea(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "景区ID必须为正整数");
        }
        ScenicArea area = scenicAreaMapper.findById(id);
        if (area == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "景区不存在");
        }
        return area;
    }

    private ScenicAreaResult toResult(ScenicArea area) {
        ScenicAreaResult result = new ScenicAreaResult();
        result.setId(area.getId());
        result.setName(area.getName());
        result.setIntro(area.getIntro());
        result.setLongitude(area.getLongitude());
        result.setLatitude(area.getLatitude());
        result.setAddress(area.getAddress());
        result.setOpenHours(area.getOpenHours());
        result.setCoverImage(area.getCoverImage());
        result.setStatus(area.getStatus());
        result.setRegion(area.getRegion());
        result.setPhone(area.getPhone());
        result.setSortOrder(area.getSortOrder());
        result.setCreatedAt(area.getCreatedAt());
        result.setUpdatedAt(area.getUpdatedAt());
        return result;
    }

    private void authorizeScenicArea(Long scenicAreaId) {
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

    private void authorizeSuperAdmin() {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"super_admin".equals(current.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅超级管理员可创建景区");
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页参数不合法");
        }
    }
}
