package com.redtour.business.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 热门问题（按提问次数倒序）。
 */
@Data
public class HotQuestionResult implements Serializable {

    private String question;
    private Long askCount;
}
