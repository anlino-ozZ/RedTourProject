package com.redtour.business.service.impl;

import com.redtour.business.common.PageResult;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.SpotRequest;
import com.redtour.business.dto.SpotResult;
import com.redtour.business.entity.Spot;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.SpotMapper;
import com.redtour.business.service.SpotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 景点管理服务实现：景区归属隔离 + 分页 + 同景区名称唯一 + 软删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotServiceImpl implements SpotService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SpotMapper spotMapper;

    @Override
    public PageResult<SpotResult> list(int page, int pageSize, Long scenicAreaId, String name, Integer status) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        Long scopedAreaId = scopeScenicAreaId(scenicAreaId);
        String normalizedName = StringUtils.hasText(name) ? name.trim() : null;
        List<Spot> rows = spotMapper.findPage(scopedAreaId, normalizedName, status, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<SpotResult> data = rows.stream().map(this::toResult).toList();
        return PageResult.of(data, spotMapper.count(scopedAreaId, normalizedName, status), page, pageSize);
    }

    @Override
    public SpotResult get(Long id) {
        Spot spot = loadSpot(id);
        authorizeSpot(spot.getScenicAreaId());
        return toResult(spot);
    }

    @Override
    public SpotResult create(SpotRequest request) {
        SysUser current = UserContext.getCurrentUser();
        Long areaId = resolveCreateScenicAreaId(current, request.getScenicAreaId());
        if (StringUtils.hasText(request.getName())
                && spotMapper.countByNameInScenicArea(areaId, request.getName().trim()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该景区内已存在同名景点");
        }
        Spot spot = new Spot();
        spot.setScenicAreaId(areaId);
        spot.setName(request.getName().trim());
        spot.setIntro(request.getIntro().trim());
        spot.setImages(blankToNull(request.getImages()));
        spot.setLongitude(request.getLongitude());
        spot.setLatitude(request.getLatitude());
        spot.setAudioUrl(request.getAudioUrl());
        spot.setWikiRef(request.getWikiRef());
        spot.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        spot.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        spot.setDeleted(0);
        spotMapper.insert(spot);
        if (spot.getId() == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "景点创建失败");
        }
        return toResult(spot);
    }

    @Override
    public SpotResult update(Long id, SpotRequest request) {
        Spot spot = loadSpot(id);
        authorizeSpot(spot.getScenicAreaId());
        if (StringUtils.hasText(request.getName()) && !request.getName().trim().equals(spot.getName())
                && spotMapper.countByNameExcludeId(spot.getScenicAreaId(), request.getName().trim(), id) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该景区内已存在同名景点");
        }
        if (request.getName() != null) {
            spot.setName(request.getName().trim());
        }
        if (request.getIntro() != null) {
            spot.setIntro(request.getIntro().trim());
        }
        if (request.getImages() != null) {
            spot.setImages(blankToNull(request.getImages()));
        }
        if (request.getLongitude() != null) {
            spot.setLongitude(request.getLongitude());
        }
        if (request.getLatitude() != null) {
            spot.setLatitude(request.getLatitude());
        }
        if (request.getAudioUrl() != null) {
            spot.setAudioUrl(request.getAudioUrl());
        }
        if (request.getWikiRef() != null) {
            spot.setWikiRef(request.getWikiRef());
        }
        if (request.getSortOrder() != null) {
            spot.setSortOrder(request.getSortOrder());
        }
        if (request.getStatus() != null) {
            if (request.getStatus() != 0 && request.getStatus() != 1) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "状态值不合法");
            }
            spot.setStatus(request.getStatus());
        }
        spotMapper.updateById(spot);
        return toResult(spot);
    }

    @Override
    public void delete(Long id) {
        Spot spot = loadSpot(id);
        authorizeSpot(spot.getScenicAreaId());
        spotMapper.softDelete(id);
    }

    @Override
    public SpotResult updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "状态值不合法");
        }
        Spot spot = loadSpot(id);
        authorizeSpot(spot.getScenicAreaId());
        spotMapper.updateStatus(id, status);
        spot.setStatus(status);
        return toResult(spot);
    }

    @Override
    public PageResult<SpotResult> publicList(int page, int pageSize, Long scenicAreaId, String keyword) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        String normalizedName = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<Spot> rows = spotMapper.findPublicPage(scenicAreaId, normalizedName, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<SpotResult> data = rows.stream().map(this::toResult).toList();
        return PageResult.of(data, spotMapper.countPublic(scenicAreaId, normalizedName), page, pageSize);
    }

    @Override
    public SpotResult publicGet(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "景点ID必须为正整数");
        }
        Spot spot = spotMapper.findPublicById(id);
        if (spot == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "景点不存在或已下架");
        }
        return toResult(spot);
    }

    private Spot loadSpot(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "景点ID必须为正整数");
        }
        Spot spot = spotMapper.findById(id);
        if (spot == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "景点不存在");
        }
        return spot;
    }

    private SpotResult toResult(Spot spot) {
        SpotResult result = new SpotResult();
        result.setId(spot.getId());
        result.setScenicAreaId(spot.getScenicAreaId());
        result.setName(spot.getName());
        result.setIntro(spot.getIntro());
        result.setImages(spot.getImages());
        result.setLongitude(spot.getLongitude());
        result.setLatitude(spot.getLatitude());
        result.setAudioUrl(spot.getAudioUrl());
        result.setWikiRef(spot.getWikiRef());
        result.setSortOrder(spot.getSortOrder());
        result.setStatus(spot.getStatus());
        result.setCreatedAt(spot.getCreatedAt());
        result.setUpdatedAt(spot.getUpdatedAt());
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
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问景点数据");
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
                throw new BusinessException(ResultCode.BAD_REQUEST, "超级管理员创建景点须指定归属景区");
            }
            return requestedAreaId;
        }
        if (!"admin".equals(current.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权创建景点");
        }
        if (current.getScenicAreaId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前管理员未分配景区");
        }
        return current.getScenicAreaId();
    }

    private void authorizeSpot(Long scenicAreaId) {
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
