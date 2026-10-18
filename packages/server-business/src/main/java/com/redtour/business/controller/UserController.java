package com.redtour.business.controller;

import com.redtour.business.annotation.RequireRole;
import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.PageResult;
import com.redtour.business.dto.UserRequest;
import com.redtour.business.dto.UserResult;
import com.redtour.business.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理接口（增删改查 + 密码变更 + 自我信息查询）。
 *
 * 路由约定：
 * - /api/v1/admin/users/**   → 仅 super_admin
 * - /api/v1/users/password    → 任何已登录用户（修改自己密码）
 * - /api/v1/users/me         → 任何已登录用户（查看自己信息）
 */
@Validated
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ==================== 管理端（仅 super_admin） ====================

    /**
     * 用户列表（分页，支持关键字筛选）。
     * 仅 super_admin 可访问。
     */
    @GetMapping("/api/v1/admin/users")
    @RequireRole({"super_admin"})
    public ApiResponse<PageResult<UserResult>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(userService.list(page, pageSize, keyword));
    }

    /**
     * 用户详情。
     * 仅 super_admin 可访问。
     */
    @GetMapping("/api/v1/admin/users/{id}")
    @RequireRole({"super_admin"})
    public ApiResponse<UserResult> get(@PathVariable @Positive(message = "用户ID必须为正整数") Long id) {
        return ApiResponse.success(userService.get(id));
    }

    /**
     * 新增用户。
     * super_admin 可创建任意角色；任何已登录用户可创建 tourist。
     */
    @PostMapping("/api/v1/admin/users")
    @RequireRole({"super_admin"})
    public ApiResponse<UserResult> create(@Valid @RequestBody UserRequest request) {
        return ApiResponse.success(userService.create(request));
    }

    /**
     * 编辑用户。
     * 仅 super_admin 可访问。
     */
    @PutMapping("/api/v1/admin/users/{id}")
    @RequireRole({"super_admin"})
    public ApiResponse<UserResult> update(
            @PathVariable @Positive(message = "用户ID必须为正整数") Long id,
            @Valid @RequestBody UserRequest request) {
        return ApiResponse.success(userService.update(id, request));
    }

    /**
     * 删除用户（软删）。
     * 仅 super_admin 可访问，且不可删除自己。
     */
    @DeleteMapping("/api/v1/admin/users/{id}")
    @RequireRole({"super_admin"})
    public ApiResponse<Void> delete(@PathVariable @Positive(message = "用户ID必须为正整数") Long id) {
        userService.delete(id);
        return ApiResponse.success(null);
    }

    /**
     * 用户状态开关（启用/禁用）。仅 super_admin 可操作。
     */
    @PostMapping("/api/v1/admin/users/{id}/status")
    @RequireRole({"super_admin"})
    public ApiResponse<Void> changeStatus(
            @PathVariable @Positive(message = "用户ID必须为正整数") Long id,
            @RequestParam int status) {
        userService.changeStatus(id, status);
        return ApiResponse.success(null);
    }

    /**
     * 重置其他用户密码（仅 super_admin）。不需要旧密码。
     */
    @PostMapping("/api/v1/admin/users/{id}/password")
    @RequireRole({"super_admin"})
    public ApiResponse<Void> resetPassword(
            @PathVariable @Positive(message = "用户ID必须为正整数") Long id,
            @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("newPassword"));
        return ApiResponse.success(null);
    }

    // ==================== 个人操作（任何已登录用户） ====================

    /**
     * 查询当前登录用户自己的信息。
     * 任何角色均可访问，等价于 GET /auth/me 但返回更完整的用户信息。
     */
    @GetMapping("/api/v1/users/me")
    @RequireRole({"super_admin", "admin", "tourist"})
    public ApiResponse<UserResult> me() {
        return ApiResponse.success(userService.me());
    }

    /**
     * 修改当前登录用户自己的密码。
     * 任何角色均可访问。
     */
    @PostMapping("/api/v1/users/password")
    @RequireRole({"super_admin", "admin", "tourist"})
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> body) {
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        userService.changePassword(oldPwd, newPwd);
        return ApiResponse.success(null);
    }
}
