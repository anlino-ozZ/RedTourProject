package com.redtour.business.dto;

import lombok.Data;

/**
 * 个人中心概览（GET /api/v1/profile 响应）。
 */
@Data
public class ProfileResult {

    private String nickname;
    private String avatar;
    private long totalVisits;
    private long totalCheckins;
    private long totalAchievements;
}
