package com.redtour.business.service;

import com.redtour.business.common.PageResult;
import com.redtour.business.dto.ProfileResult;
import com.redtour.business.dto.SpotFavoriteResult;
import com.redtour.business.dto.VisitRecordResult;

/**
 * 个人中心服务（需登录）。
 */
public interface ProfileService {

    /** 个人概览：昵称 / 头像 / 总游览数 / 总打卡数 / 总成就数。 */
    ProfileResult getProfile(Long userId);

    /** 当前用户游览记录分页。 */
    PageResult<VisitRecordResult> listRecords(Long userId, int page, int pageSize);

    /** 当前用户收藏景点分页（join spot）。 */
    PageResult<SpotFavoriteResult> listFavorites(Long userId, int page, int pageSize);
}
