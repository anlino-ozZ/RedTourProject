package com.redtour.business.controller;

import com.redtour.business.annotation.RequireRole;
import com.redtour.business.common.ApiResponse;
import com.redtour.business.dto.LoginRequest;
import com.redtour.business.dto.LoginResult;
import com.redtour.business.dto.RegisterRequest;
import com.redtour.business.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 鉴权接口（与接口文档第二章对齐）
 * 基址：/api/v1/auth
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录获取 JWT（无鉴权）
     * 接口文档：POST /auth/login
     */
    @PostMapping("/login")
    public ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.success(authService.login(req));
    }

    /**
     * 游客自助注册（无鉴权，仅允许注册 tourist 角色）
     */
    @PostMapping("/register")
    public ApiResponse<LoginResult> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.success(authService.register(req));
    }

    /**
     * 获取当前登录用户信息（需鉴权）
     * 接口文档：GET /auth/me
     */
    @GetMapping("/me")
    public ApiResponse<LoginResult> me() {
        return ApiResponse.success(authService.me());
    }

    /**
     * 登出（需鉴权，任何角色）
     * 前端清除本地 token，同时后端将 token 写入 Redis 黑名单
     * 接口文档：POST /auth/logout
     */
    @PostMapping("/logout")
    @RequireRole({"super_admin", "admin", "tourist"})
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : "";
        authService.logout(token);
        return ApiResponse.success(null);
    }
}
