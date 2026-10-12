package com.redtour.business.entity;

import lombok.Data;

/**
 * 推荐问题实体，对应 recommendation_question 表。
 */
@Data
public class RecommendationQuestion {

    private Long id;
    private Long scenicAreaId;
    private String question;
    private Integer sortWeight;
}
