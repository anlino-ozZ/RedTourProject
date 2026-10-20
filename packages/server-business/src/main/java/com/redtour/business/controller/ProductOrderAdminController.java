package com.redtour.business.controller;

import com.redtour.business.annotation.RequireRole;
import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.PageResult;
import com.redtour.business.dto.ProductOrderResult;
import com.redtour.business.service.ProductOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 特产意向订单管理端接口（@RequireRole，scenicAreaId 隔离）。
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequireRole({"super_admin", "admin"})
@RequiredArgsConstructor
public class ProductOrderAdminController {

    private final ProductOrderService productOrderService;

    /** 订单分页列表（scenicAreaId 隔离，status 可选）。 */
    @GetMapping
    public ApiResponse<PageResult<ProductOrderResult>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long scenicAreaId,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(productOrderService.adminList(page, pageSize, scenicAreaId, status));
    }

    /** 订单详情（scenicAreaId 隔离）。 */
    @GetMapping("/{id}")
    public ApiResponse<ProductOrderResult> get(@PathVariable Long id) {
        return ApiResponse.success(productOrderService.adminGet(id));
    }

    /** 更新订单状态 + 处理备注。 */
    @PutMapping("/{id}/status")
    public ApiResponse<ProductOrderResult> updateStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String handleRemark) {
        return ApiResponse.success(productOrderService.adminUpdateStatus(id, status, handleRemark));
    }
}
