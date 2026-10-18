package com.redtour.business.service.impl;

import com.redtour.business.common.PageResult;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.UserRequest;
import com.redtour.business.dto.UserResult;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.UserMapper;
import com.redtour.business.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户管理服务实现。
 * - list/get/create/update/delete：仅 super_admin
 * - changePassword：任何角色，仅修改本人
 * - me：任何角色
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final UserMapper userMapper;

    @Override
    public PageResult<UserResult> list(int page, int pageSize, String keyword) {
        requireSuperAdmin();
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<SysUser> rows = userMapper.findPage(kw, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<UserResult> data = rows.stream().map(this::toResult).toList();
        return PageResult.of(data, userMapper.count(kw), page, pageSize);
    }

    @Override
    public UserResult get(Long id) {
        requireSuperAdmin();
        SysUser user = loadUser(id);
        return toResult(user);
    }

    @Override
    public UserResult create(UserRequest request) {
        requireSuperAdmin();
        String role = request.getRole();

        // 用户名唯一
        if (userMapper.countByUsername(request.getUsername().trim()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }

        // admin 角色需绑定景区
        if ("admin".equals(role) && request.getScenicAreaId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "景区管理员需指定管辖景区");
        }

        // 密码：新建必填
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码不能为空");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername().trim());
        user.setPassword(PASSWORD_ENCODER.encode(request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getUsername());
        user.setRole(role);
        user.setScenicAreaId(request.getScenicAreaId());
        user.setAvatar(request.getAvatar());
        user.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        user.setDeleted(0);
        userMapper.insert(user);

        log.info("[用户管理] super_admin {} 创建用户 id={} role={}", UserContext.getCurrentUser().getId(), user.getId(), role);
        return toResult(user);
    }

    @Override
    public UserResult update(Long id, UserRequest request) {
        requireSuperAdmin();
        SysUser user = loadUser(id);

        // 用户名唯一性（排除自己）
        if (!request.getUsername().trim().equals(user.getUsername())
                && userMapper.countByUsernameExcludeId(request.getUsername().trim(), id) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }

        // admin 角色需绑定景区
        if ("admin".equals(request.getRole()) && request.getScenicAreaId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "景区管理员需指定管辖景区");
        }

        user.setUsername(request.getUsername().trim());
        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname());
        }
        user.setRole(request.getRole());
        user.setScenicAreaId(request.getScenicAreaId());
        user.setAvatar(request.getAvatar());
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        // 密码变更（可选）
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(PASSWORD_ENCODER.encode(request.getPassword()));
        }
        userMapper.updateById(user);

        log.info("[用户管理] super_admin {} 更新用户 id={}", UserContext.getCurrentUser().getId(), id);
        return toResult(user);
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        SysUser current = requireLogin();
        SysUser dbUser = userMapper.findById(current.getId());
        if (dbUser == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!PASSWORD_ENCODER.matches(oldPassword, dbUser.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "旧密码不正确");
        }
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 32) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码 6-32 字符");
        }
        userMapper.updatePassword(current.getId(), PASSWORD_ENCODER.encode(newPassword));
        log.info("[用户管理] 用户 {} 修改了自己的密码", current.getId());
    }

    @Override
    public void delete(Long id) {
        requireSuperAdmin();
        loadUser(id);
        // 禁止删除自己
        SysUser current = UserContext.getCurrentUser();
        if (current != null && current.getId().equals(id)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能删除自己的账号");
        }
        userMapper.softDelete(id);
        log.info("[用户管理] super_admin {} 删除了用户 id={}", current.getId(), id);
    }

    @Override
    public void changeStatus(Long id, int status) {
        requireSuperAdmin();
        loadUser(id);
        if (status != 0 && status != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "状态值不合法");
        }
        userMapper.updateStatus(id, status);
        log.info("[用户管理] super_admin {} 修改用户 {} 状态为 {}", UserContext.getCurrentUser().getId(), id, status);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        requireSuperAdmin();
        loadUser(id);
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 32) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码 6-32 字符");
        }
        userMapper.updatePassword(id, PASSWORD_ENCODER.encode(newPassword));
        log.info("[用户管理] super_admin {} 重置了用户 {} 的密码", UserContext.getCurrentUser().getId(), id);
    }

    @Override
    public UserResult me() {
        SysUser current = requireLogin();
        SysUser user = userMapper.findById(current.getId());
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return toResult(user);
    }

    // ==================== 私有工具 ====================

    private SysUser loadUser(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户ID必须为正整数");
        }
        SysUser user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private UserResult toResult(SysUser user) {
        UserResult r = new UserResult();
        r.setId(user.getId());
        r.setUsername(user.getUsername());
        r.setNickname(user.getNickname());
        r.setRole(user.getRole());
        r.setScenicAreaId(user.getScenicAreaId());
        r.setAvatar(user.getAvatar());
        r.setStatus(user.getStatus());
        r.setCreatedAt(user.getCreatedAt());
        r.setUpdatedAt(user.getUpdatedAt());
        return r;
    }

    private SysUser requireLogin() {
        SysUser user = UserContext.getCurrentUser();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    private void requireSuperAdmin() {
        SysUser user = UserContext.getCurrentUser();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"super_admin".equals(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅超级管理员可执行此操作");
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页参数不合法");
        }
    }
}
