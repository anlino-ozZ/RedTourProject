package com.redtour.business.service;

import com.redtour.business.dto.AnalyticsVisitsResult;
import com.redtour.business.dto.HotQuestionResult;
import com.redtour.business.dto.HotSpotResult;
import com.redtour.business.dto.ProductClickResult;

import java.util.List;

/**
 * 运营分析服务（scenicAreaId 隔离）。
 */
public interface AnalyticsService {

    /** 游览量按天聚合。 */
    AnalyticsVisitsResult visits(Long scenicAreaId, String startDate, String endDate);

    /** 热门景点（按打卡次数聚合）。 */
    List<HotSpotResult> hotSpots(Long scenicAreaId);

    /** 热门问题 topN（来自 popular_question 表），按 scenicAreaId 隔离。 */
    List<HotQuestionResult> hotQuestions(Long scenicAreaId, int topN);

    /** 产品点击排行。 */
    List<ProductClickResult> products(Long scenicAreaId);
}
