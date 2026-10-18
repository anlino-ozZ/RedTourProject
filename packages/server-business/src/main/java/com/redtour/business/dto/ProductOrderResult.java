package com.redtour.business.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 特产意向订单响应（管理端 / 游客端共用，含下单时产品快照）。
 */
@Data
public class ProductOrderResult {

    private Long id;
    private Long scenicAreaId;
    private Long productId;
    /** 下单时刻产品快照（原样 JSON 字符串：{name, price, image}） */
    private String productSnapshot;
    private Long userId;
    private Integer quantity;
    private String contactName;
    private String contactPhone;
    private String remark;
    /** pending 待处理 / contacted 已联系 / closed 已关闭 */
    private String status;
    private String handleRemark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
