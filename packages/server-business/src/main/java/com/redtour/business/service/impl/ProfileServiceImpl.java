package com.redtour.business.service.impl;

import com.redtour.business.common.PageResult;
import com.redtour.business.common.ResultCode;
import com.redtour.business.dto.ProfileResult;
import com.redtour.business.dto.SpotFavoriteResult;
import com.redtour.business.dto.VisitRecordResult;
import com.redtour.business.entity.SysUser;
import com.redtour.business.entity.VisitRecord;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.CheckinMapper;
import com.redtour.business.mapper.SpotFavoriteMapper;
import com.redtour.business.mapper.UserAchievementMapper;
import com.redtour.business.mapper.UserMapper;
import com.redtour.business.mapper.VisitRecordMapper;
import com.redtour.business.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 个人中心服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserMapper userMapper;
    private final VisitRecordMapper visitRecordMapper;
    private final CheckinMapper checkinMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final SpotFavoriteMapper spotFavoriteMapper;

    @Override
    public ProfileResult getProfile(Long userId) {
        SysUser user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        ProfileResult result = new ProfileResult();
        result.setNickname(user.getNickname());
        result.setAvatar(user.getAvatar());
        result.setTotalVisits(visitRecordMapper.countByUser(userId));
        result.setTotalCheckins(checkinMapper.countByUser(userId));
        result.setTotalAchievements(userAchievementMapper.countByUserId(userId));
        return result;
    }

    @Override
    public PageResult<VisitRecordResult> listRecords(Long userId, int page, int pageSize) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        List<VisitRecordResult> data = visitRecordMapper.findByUserId(userId, offset, pageSize).stream()
                .map(this::toResultVisitRecord)
                .toList();
        return PageResult.of(data, visitRecordMapper.countByUser(userId), page, pageSize);
    }

    @Override
    public PageResult<SpotFavoriteResult> listFavorites(Long userId, int page, int pageSize) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        List<SpotFavoriteResult> rows = spotFavoriteMapper.findByUserId(userId, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        long total = spotFavoriteMapper.countByUserId(userId);
        return PageResult.of(rows, total, page, pageSize);
    }

    private VisitRecordResult toResultVisitRecord(VisitRecord record) {
        VisitRecordResult result = new VisitRecordResult();
        result.setId(record.getId());
        result.setUserId(record.getUserId());
        result.setScenicAreaId(record.getScenicAreaId());
        result.setRouteId(record.getRouteId());
        result.setSpotCount(record.getSpotCount());
        result.setDuration(record.getDuration());
        result.setCreatedAt(record.getCreatedAt());
        return result;
    }

    private void validatePage(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页参数不合法");
        }
    }
}
