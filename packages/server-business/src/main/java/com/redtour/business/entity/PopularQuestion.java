package com.redtour.business.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答日志聚合出的热门问题。
 */
@Data
public class PopularQuestion {

    private Long id;
    private String question;
    private Long askCount;
    private LocalDateTime latestAskedAt;
}
