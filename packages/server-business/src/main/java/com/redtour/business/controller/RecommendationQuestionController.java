package com.redtour.business.controller;

import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.ResultCode;
import com.redtour.business.dto.RecommendationQuestionResult;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.service.RecommendationQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 推荐问题公开接口。
 */
@RestController
@RequestMapping("/api/v1/ask/recommendations")
@RequiredArgsConstructor
public class RecommendationQuestionController {

    private final RecommendationQuestionService recommendationQuestionService;

    /** 查询指定景区的推荐问题。 */
    @GetMapping
    public ApiResponse<List<RecommendationQuestionResult>> listRecommendations(
            @RequestParam("scenicAreaId") Long scenicAreaId) {
        if (scenicAreaId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "景区ID必须为正整数");
        }
        return ApiResponse.success(recommendationQuestionService.listRecommendations(scenicAreaId));
    }
}
