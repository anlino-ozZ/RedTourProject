package com.redtour.business.dto;

import lombok.Data;

import java.util.List;

/**
 * 打卡结果响应。
 */
@Data
public class CheckinResult {

    private Long spotId;
    private boolean checked;
    /** 进度，形如 "k/total" */
    private String progress;
    /** 本次解锁的成就标题列表 */
    private List<String> achievementUnlocked;
}
