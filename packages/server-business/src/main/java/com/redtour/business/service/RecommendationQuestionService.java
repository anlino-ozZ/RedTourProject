package com.redtour.business.service;

import com.redtour.business.dto.RecommendationQuestionResult;

import java.util.List;

/**
 * 推荐问题业务服务。
 */
public interface RecommendationQuestionService {

    /** 查询指定景区的动态热门问题，不足时由静态配置和离线问题补足。 */
    List<RecommendationQuestionResult> listRecommendations(Long scenicAreaId);
}
