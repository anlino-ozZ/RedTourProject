package com.redtour.business.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 热门景点（按打卡次数聚合）。
 */
@Data
public class HotSpotResult implements Serializable {

    private Long spotId;
    private String spotName;
    private Long visitCount;
}
