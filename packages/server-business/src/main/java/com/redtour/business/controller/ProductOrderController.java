package com.redtour.business.controller;

import com.redtour.business.common.ApiResponse;
import com.redtour.business.dto.ProductOrderRequest;
import com.redtour.business.dto.ProductOrderResult;
import com.redtour.business.service.ProductOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 特产意向订单游客端接口（需登录，无 @RequireRole，由 AuthInterceptor 校验 JWT）。
 * 路径为两段（/api/v1/products/{id}/order），不在白名单，自动走 JWT 鉴权。
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductOrderController {

    private final ProductOrderService productOrderService;

    /** 游客对指定产品下单（需登录）。 */
    @PostMapping("/{id}/order")
    public ApiResponse<ProductOrderResult> order(
            @PathVariable Long id,
            @Valid @RequestBody ProductOrderRequest request) {
        return ApiResponse.success(productOrderService.createOrder(id, request));
    }
}
