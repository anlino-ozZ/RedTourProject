package com.redtour.business.controller;

import com.redtour.business.common.ApiResponse;
import com.redtour.business.dto.CheckinRequest;
import com.redtour.business.dto.CheckinResult;
import com.redtour.business.dto.RouteCompleteResult;
import com.redtour.business.service.CheckinService;
import com.redtour.business.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 路线交互接口（打卡 / 完成，需登录，无 @RequireRole，由 AuthInterceptor 校验 JWT）。
 * 路径为两段（/api/v1/routes/{id}/checkin、/api/v1/routes/{id}/complete），
 * 不在白名单，自动走 JWT 鉴权。
 */
@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
public class RouteInteractionController {

    private final CheckinService checkinService;
    private final RouteService routeService;

    /** 路线景点打卡（需登录）。 */
    @PostMapping("/{id}/checkin")
    public ApiResponse<CheckinResult> checkin(
            @PathVariable Long id,
            @Valid @RequestBody CheckinRequest request) {
        return ApiResponse.success(checkinService.checkin(id, request));
    }

    /** 路线完成（需登录）：全部景点已打卡后写入游览记录。 */
    @PostMapping("/{id}/complete")
    public ApiResponse<RouteCompleteResult> complete(@PathVariable Long id) {
        return ApiResponse.success(routeService.complete(id));
    }
}
