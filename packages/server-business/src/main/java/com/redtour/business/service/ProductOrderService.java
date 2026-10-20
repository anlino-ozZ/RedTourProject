package com.redtour.business.service;

import com.redtour.business.common.PageResult;
import com.redtour.business.dto.ProductOrderRequest;
import com.redtour.business.dto.ProductOrderResult;

/**
 * 特产意向订单服务（游客下单 + 管理端处理）。
 */
public interface ProductOrderService {

    /** 游客下单（需登录）：落库含产品快照，status='pending'。 */
    ProductOrderResult createOrder(Long productId, ProductOrderRequest request);

    /** 管理端：分页列表（scenicAreaId 隔离，status 可选）。 */
    PageResult<ProductOrderResult> adminList(int page, int pageSize, Long scenicAreaId, String status);

    /** 管理端：订单详情（scenicAreaId 隔离）。 */
    ProductOrderResult adminGet(Long id);

    /** 管理端：更新订单状态 + 处理备注。 */
    ProductOrderResult adminUpdateStatus(Long id, String status, String handleRemark);
}
