package com.redtour.business.dto;

import lombok.Value;

/**
 * 面向游客端的推荐问题。
 */
@Value
public class RecommendationQuestionResult {

    Long id;
    String question;
}
