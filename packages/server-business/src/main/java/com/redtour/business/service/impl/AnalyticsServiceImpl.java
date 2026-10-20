package com.redtour.business.service.impl;

import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.AnalyticsVisitsResult;
import com.redtour.business.dto.HotQuestionResult;
import com.redtour.business.dto.HotSpotResult;
import com.redtour.business.dto.ProductClickResult;
import com.redtour.business.entity.PopularQuestion;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.AnalyticsMapper;
import com.redtour.business.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 运营分析服务实现：景区隔离 + 四类聚合查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final int MAX_TOP_N = 100;

    private final AnalyticsMapper analyticsMapper;

    @Override
    public AnalyticsVisitsResult visits(Long scenicAreaId, String startDate, String endDate) {
        Long scopedAreaId = scopeScenicAreaId(scenicAreaId);
        List<Map<String, Object>> rows = analyticsMapper.countVisitsByDay(scopedAreaId, startDate, endDate);
        List<String> dates = new ArrayList<>();
        List<Long> series = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object dateObj = row.get("visit_date");
                Object countObj = row.get("visit_count");
                dates.add(dateObj == null ? null : dateObj.toString());
                series.add(countObj == null ? 0L : ((Number) countObj).longValue());
            }
        }
        AnalyticsVisitsResult result = new AnalyticsVisitsResult();
        result.setDates(dates);
        result.setSeries(series);
        return result;
    }

    @Override
    public List<HotSpotResult> hotSpots(Long scenicAreaId) {
        Long scopedAreaId = scopeScenicAreaId(scenicAreaId);
        List<HotSpotResult> rows = analyticsMapper.countBySpot(scopedAreaId);
        return rows == null ? List.of() : rows;
    }

    @Override
    public List<HotQuestionResult> hotQuestions(int topN) {
        if (topN <= 0) {
            topN = 10;
        }
        if (topN > MAX_TOP_N) {
            topN = MAX_TOP_N;
        }
        List<PopularQuestion> rows = analyticsMapper.findTopQuestions(topN);
        if (rows == null) {
            return List.of();
        }
        List<HotQuestionResult> result = new ArrayList<>(rows.size());
        for (PopularQuestion q : rows) {
            HotQuestionResult r = new HotQuestionResult();
            r.setQuestion(q.getQuestion());
            r.setAskCount(q.getAskCount());
            result.add(r);
        }
        return result;
    }

    @Override
    public List<ProductClickResult> products(Long scenicAreaId) {
        Long scopedAreaId = scopeScenicAreaId(scenicAreaId);
        List<ProductClickResult> rows = analyticsMapper.findProductsByClicks(scopedAreaId);
        return rows == null ? List.of() : rows;
    }

    /** admin 强制本人景区；super 透传筛选条件（可 null 查全量）。 */
    private Long scopeScenicAreaId(Long scenicAreaId) {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if ("super_admin".equals(current.getRole())) {
            return scenicAreaId;
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
