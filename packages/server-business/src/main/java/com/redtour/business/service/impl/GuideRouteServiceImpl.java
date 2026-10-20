package com.redtour.business.service.impl;

import com.redtour.business.common.PageResult;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.GuideRouteRequest;
import com.redtour.business.dto.GuideRouteResult;
import com.redtour.business.entity.GuideRoute;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.GuideRouteMapper;
import com.redtour.business.service.GuideRouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 导览路线管理服务实现：景区归属隔离 + 分页 + 下线（status=0）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuideRouteServiceImpl implements GuideRouteService {

    private static final int MAX_PAGE_SIZE = 100;

    private final GuideRouteMapper guideRouteMapper;

    @Override
    public PageResult<GuideRouteResult> list(int page, int pageSize, String name, Long scenicAreaId, Integer status) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        Long scopedAreaId = scopeScenicAreaId(scenicAreaId);
        String normalizedName = StringUtils.hasText(name) ? name.trim() : null;
        List<GuideRoute> rows = guideRouteMapper.findPage(scopedAreaId, normalizedName, status, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<GuideRouteResult> data = rows.stream().map(this::toResult).toList();
        return PageResult.of(data, guideRouteMapper.count(scopedAreaId, normalizedName, status), page, pageSize);
    }

    @Override
    public GuideRouteResult get(Long id) {
        GuideRoute route = loadRoute(id);
        authorizeGuideRoute(route.getScenicAreaId());
        return toResult(route);
    }

    @Override
    public GuideRouteResult create(GuideRouteRequest request) {
        SysUser current = UserContext.getCurrentUser();
        Long areaId = resolveCreateScenicAreaId(current, request.getScenicAreaId());
        GuideRoute route = new GuideRoute();
        route.setScenicAreaId(areaId);
        route.setName(request.getName().trim());
        route.setDescription(blankToNull(request.getDescription()));
        route.setSpotIds(blankToNull(request.getSpotIds()));
        route.setDuration(request.getDuration());
        route.setDifficulty(request.getDifficulty().trim());
        route.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        guideRouteMapper.insert(route);
        if (route.getId() == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "路线创建失败");
        }
        return toResult(route);
    }

    @Override
    public GuideRouteResult update(Long id, GuideRouteRequest request) {
        GuideRoute route = loadRoute(id);
        authorizeGuideRoute(route.getScenicAreaId());
        if (request.getName() != null) {
            route.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            route.setDescription(blankToNull(request.getDescription()));
        }
        if (request.getSpotIds() != null) {
            route.setSpotIds(blankToNull(request.getSpotIds()));
        }
        if (request.getDuration() != null) {
            route.setDuration(request.getDuration());
        }
        if (request.getDifficulty() != null) {
            route.setDifficulty(request.getDifficulty().trim());
        }
        if (request.getStatus() != null) {
            if (request.getStatus() != 0 && request.getStatus() != 1) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "状态值不合法");
            }
            route.setStatus(request.getStatus());
        }
        guideRouteMapper.updateById(route);
        return toResult(route);
    }

    @Override
    public void delete(Long id) {
        GuideRoute route = loadRoute(id);
        authorizeGuideRoute(route.getScenicAreaId());
        guideRouteMapper.delete(id);
    }

    @Override
    public GuideRouteResult updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "状态值不合法");
        }
        GuideRoute route = loadRoute(id);
        authorizeGuideRoute(route.getScenicAreaId());
        guideRouteMapper.updateStatus(id, status);
        route.setStatus(status);
        return toResult(route);
    }

    @Override
    public PageResult<GuideRouteResult> publicList(int page, int pageSize, Long scenicAreaId, String keyword) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        String normalizedName = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<GuideRoute> rows = guideRouteMapper.findPublicPage(scenicAreaId, normalizedName, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<GuideRouteResult> data = rows.stream().map(this::toResult).toList();
        return PageResult.of(data, guideRouteMapper.countPublic(scenicAreaId, normalizedName), page, pageSize);
    }

    @Override
    public GuideRouteResult publicGet(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "路线ID必须为正整数");
        }
        GuideRoute route = guideRouteMapper.findPublicById(id);
        if (route == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "路线不存在或已下架");
        }
        return toResult(route);
    }

    private GuideRoute loadRoute(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "路线ID必须为正整数");
        }
        GuideRoute route = guideRouteMapper.findById(id);
        if (route == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "路线不存在");
        }
        return route;
    }

    private GuideRouteResult toResult(GuideRoute route) {
        GuideRouteResult result = new GuideRouteResult();
        result.setId(route.getId());
        result.setScenicAreaId(route.getScenicAreaId());
        result.setName(route.getName());
        result.setDescription(route.getDescription());
        result.setSpotIds(route.getSpotIds());
        result.setDuration(route.getDuration());
        result.setDifficulty(route.getDifficulty());
        result.setStatus(route.getStatus());
        result.setCreatedAt(route.getCreatedAt());
        result.setUpdatedAt(route.getUpdatedAt());
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
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问路线数据");
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
                throw new BusinessException(ResultCode.BAD_REQUEST, "超级管理员创建路线须指定归属景区");
            }
            return requestedAreaId;
        }
        if (!"admin".equals(current.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权创建路线");
        }
        if (current.getScenicAreaId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前管理员未分配景区");
        }
        return current.getScenicAreaId();
    }

    private void authorizeGuideRoute(Long scenicAreaId) {
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
