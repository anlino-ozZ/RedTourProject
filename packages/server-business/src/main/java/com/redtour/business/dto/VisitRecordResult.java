package com.redtour.business.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游览记录响应（个人中心 GET /api/v1/profile/records 返回项）。
 */
@Data
public class VisitRecordResult {

    private Long id;
    private Long userId;
    private Long scenicAreaId;
    private Long routeId;
    private Integer spotCount;
    private Integer duration;
    private LocalDateTime createdAt;
}
