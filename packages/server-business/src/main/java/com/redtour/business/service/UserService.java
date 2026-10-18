package com.redtour.business.service;

import com.redtour.business.common.PageResult;
import com.redtour.business.dto.UserRequest;
import com.redtour.business.dto.UserResult;

/**
 * 用户管理服务接口（仅 super_admin 可访问）。
 */
public interface UserService {

    /**
     * 分页查询用户列表（仅 super_admin）。
     *
     * @param page    页码
     * @param pageSize 每页条数
     * @param keyword 用户名/昵称关键字，可空
     * @return 分页结果
     */
    PageResult<UserResult> list(int page, int pageSize, String keyword);

    /**
     * 查询单个用户详情（仅 super_admin）。
     *
     * @param id 用户ID
     * @return 用户信息
     */
    UserResult get(Long id);

    /**
     * 新增用户（仅 super_admin 可创建 admin/super_admin，admin/tourist 可创建 tourist）。
     *
     * @param request 用户请求
     * @return 新用户信息
     */
    UserResult create(UserRequest request);

    /**
     * 编辑用户（仅 super_admin）。
     *
     * @param id      用户ID
     * @param request 更新内容
     * @return 更新后用户信息
     */
    UserResult update(Long id, UserRequest request);

    /**
     * 修改当前登录用户自己的密码。
     *
     * @param oldPassword 旧密码（校验）
     * @param newPassword 新密码
     */
    void changePassword(String oldPassword, String newPassword);

    /**
     * 删除用户（软删）（仅 super_admin）。
     *
     * @param id 用户ID
     */
    void delete(Long id);

    /**
     * 修改用户状态（启用/禁用），仅 super_admin。
     *
     * @param id     用户ID
     * @param status 0=禁用 1=启用
     */
    void changeStatus(Long id, int status);

    /**
     * 重置其他用户密码（仅 super_admin，不需要旧密码）。
     *
     * @param id          用户ID
     * @param newPassword 新密码，6-32字符
     */
    void resetPassword(Long id, String newPassword);

    /**
     * 获取当前登录用户自己的信息（任何角色）。
     *
     * @return 当前用户信息
     */
    UserResult me();
}
