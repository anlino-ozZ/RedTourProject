package com.redtour.business.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 特产意向订单实体，对应 product_order 表。
 * productSnapshot 在数据库中以 JSON 字符串保存下单时刻产品快照。
 */
@Data
public class ProductOrder {

    /** 主键 */
    private Long id;

    /** 归属景区 ID */
    private Long scenicAreaId;

    /** 产品 ID */
    private Long productId;

    /** 下单时刻产品快照（JSON 字符串，名称/价格/图片） */
    private String productSnapshot;

    /** 游客用户 ID（可空，未登录游客也能下单） */
    private Long userId;

    /** 数量 */
    private Integer quantity;

    /** 联系人姓名 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 用户备注 */
    private String remark;

    /** 订单状态: pending 待处理 / contacted 已联系 / closed 已关闭 */
    private String status;

    /** 商家跟进处理备注 */
    private String handleRemark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}