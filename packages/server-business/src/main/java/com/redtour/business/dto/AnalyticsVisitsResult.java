package com.redtour.business.dto;

import lombok.Data;

import java.util.List;

/**
 * 游览量按天聚合结果：dates 与 series 按索引对齐。
 */
@Data
public class AnalyticsVisitsResult {

    private List<String> dates;
    private List<Long> series;
}
