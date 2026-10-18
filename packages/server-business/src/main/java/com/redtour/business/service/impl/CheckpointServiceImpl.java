package com.redtour.business.service.impl;

import com.redtour.business.common.PageResult;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.CheckpointRequest;
import com.redtour.business.dto.CheckpointResult;
import com.redtour.business.entity.Checkpoint;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.CheckpointMapper;
import com.redtour.business.service.CheckpointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 打卡点管理服务实现。
 *
 * checkpoint 表无 scenic_area_id 列，景区归属通过 spot 表间接确定；
 * 因此权限隔离以"spot 所属景区"为准：admin 仅能访问本人景区下的 spot 对应的打卡点。
 * 列表的 scenicAreaId 筛选通过 scopeScenicAreaId 强制 admin 本人景区，并在 SQL 中关联 spot 过滤。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckpointServiceImpl implements CheckpointService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_RADIUS = 50;

    private final CheckpointMapper checkpointMapper;

    @Override
    public PageResult<CheckpointResult> list(int page, int pageSize, Long scenicAreaId, Long spotId) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        Long scopedAreaId = scopeScenicAreaId(scenicAreaId);
        List<Checkpoint> rows = checkpointMapper.findPage(scopedAreaId, spotId, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<CheckpointResult> data = rows.stream().map(this::toResult).toList();
        return PageResult.of(data, checkpointMapper.count(scopedAreaId, spotId), page, pageSize);
    }

    @Override
    public CheckpointResult get(Long id) {
        Checkpoint checkpoint = loadCheckpoint(id);
        authorizeSpotArea(checkpoint.getSpotId());
        return toResult(checkpoint);
    }

    @Override
    public CheckpointResult create(CheckpointRequest request) {
        // 通过 spot 反查归属景区并做隔离校验（super 放行；admin 必须本人景区）
        authorizeSpotArea(request.getSpotId());
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setSpotId(request.getSpotId());
        checkpoint.setLongitude(request.getLongitude());
        checkpoint.setLatitude(request.getLatitude());
        checkpoint.setRadius(request.getRadius() == null ? DEFAULT_RADIUS : request.getRadius());
        checkpointMapper.insert(checkpoint);
        if (checkpoint.getId() == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "打卡点创建失败");
        }
        return toResult(checkpoint);
    }

    @Override
    public CheckpointResult update(Long id, CheckpointRequest request) {
        Checkpoint checkpoint = loadCheckpoint(id);
        authorizeSpotArea(checkpoint.getSpotId());
        if (request.getSpotId() != null) {
            // 若更换景点，需对新景点重新做隔离校验
            authorizeSpotArea(request.getSpotId());
            checkpoint.setSpotId(request.getSpotId());
        }
        if (request.getLongitude() != null) {
            checkpoint.setLongitude(request.getLongitude());
        }
        if (request.getLatitude() != null) {
            checkpoint.setLatitude(request.getLatitude());
        }
        if (request.getRadius() != null) {
            checkpoint.setRadius(request.getRadius());
        }
        checkpointMapper.updateById(checkpoint);
        return toResult(checkpoint);
    }

    @Override
    public void delete(Long id) {
        Checkpoint checkpoint = loadCheckpoint(id);
        authorizeSpotArea(checkpoint.getSpotId());
        checkpointMapper.delete(id);
    }

    private Checkpoint loadCheckpoint(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "打卡点ID必须为正整数");
        }
        Checkpoint checkpoint = checkpointMapper.findById(id);
        if (checkpoint == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "打卡点不存在");
        }
        return checkpoint;
    }

    private CheckpointResult toResult(Checkpoint checkpoint) {
        CheckpointResult result = new CheckpointResult();
        result.setId(checkpoint.getId());
        result.setSpotId(checkpoint.getSpotId());
        result.setLongitude(checkpoint.getLongitude());
        result.setLatitude(checkpoint.getLatitude());
        result.setRadius(checkpoint.getRadius());
        result.setCreatedAt(checkpoint.getCreatedAt());
        result.setUpdatedAt(checkpoint.getUpdatedAt());
        return result;
    }

    /** 通过 spot 反查归属景区并校验当前用户是否有权访问（super 放行；admin 仅本人景区）。 */
    private void authorizeSpotArea(Long spotId) {
        if (spotId == null || spotId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "关联景点ID不合法");
        }
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long spotAreaId = checkpointMapper.selectScenicAreaIdBySpotId(spotId);
        if (spotAreaId == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "关联景点不存在");
        }
        if ("super_admin".equals(current.getRole())) {
            return;
        }
        if (!"admin".equals(current.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问打卡点数据");
        }
        if (current.getScenicAreaId() == null || !current.getScenicAreaId().equals(spotAreaId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他景区的数据");
        }
    }

    /** 列表景区筛选：admin 强制本人景区；super 透传筛选条件。 */
    private Long scopeScenicAreaId(Long scenicAreaId) {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if ("super_admin".equals(current.getRole())) {
            return scenicAreaId;
        }
        if (!"admin".equals(current.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问打卡点数据");
        }
        if (current.getScenicAreaId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前管理员未分配景区");
        }
        return current.getScenicAreaId();
    }

    private void validatePage(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页参数不合法");
        }
    }
}
