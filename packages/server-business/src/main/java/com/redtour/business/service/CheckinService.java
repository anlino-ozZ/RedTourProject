package com.redtour.business.service;

import com.redtour.business.dto.CheckinRequest;
import com.redtour.business.dto.CheckinResult;
import com.redtour.business.entity.Checkpoint;

import java.math.BigDecimal;
import java.util.List;

/**
 * 路线打卡服务（需登录）。
 */
public interface CheckinService {

    /** 在路线某景点打卡：校验景点归属 + 距离打卡点半径内 → 写 checkin + 返回进度。 */
    CheckinResult checkin(Long routeId, CheckinRequest request);

    /**
     * 解析某景点下、给定坐标命中的打卡点列表（半径内）。
     * 供在线打卡与离线同步共用，避免两处重复 haversine 逻辑。
     */
    List<Checkpoint> resolveMatchedCheckpoints(Long spotId, BigDecimal longitude, BigDecimal latitude);

    /**
     * 统计某用户在指定景点集合内「所有打卡点均已打到」的景点数。
     * 用于路线进度展示与路线完成判定（brief B2：景点下全部打卡点命中才算该景点完成）。
     */
    int countCompletedSpots(Long userId, List<Long> spotIds);
}
