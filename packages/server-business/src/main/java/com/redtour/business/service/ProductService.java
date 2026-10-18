package com.redtour.business.service;

import com.redtour.business.common.PageResult;
import com.redtour.business.dto.ProductRequest;
import com.redtour.business.dto.ProductResult;

/**
 * 特产/文创产品管理服务（带景区归属隔离）。
 */
public interface ProductService {

    /** 管理端分页列表（名称/景区/状态筛选；admin 仅本人景区）。 */
    PageResult<ProductResult> list(int page, int pageSize, String name, Long scenicAreaId, Integer status);

    /** 管理端详情。 */
    ProductResult get(Long id);

    /** 新增产品（admin 限本景区；super 可指定景区）。 */
    ProductResult create(ProductRequest request);

    /** 编辑产品（admin 限本景区；scenic_area_id 不变）。 */
    ProductResult update(Long id, ProductRequest request);

    /** 下线（status=0）。 */
    void delete(Long id);

    /** 上下架状态切换（admin 限本景区）。 */
    ProductResult updateStatus(Long id, Integer status);

    /** 游客端：上架产品分页（可按景区/品类/名称模糊筛选）。 */
    PageResult<ProductResult> publicList(int page, int pageSize, Long scenicAreaId, String category, String keyword);

    /** 游客端：上架产品详情（浏览量 click_count +1）。 */
    ProductResult publicGet(Long id);
}
