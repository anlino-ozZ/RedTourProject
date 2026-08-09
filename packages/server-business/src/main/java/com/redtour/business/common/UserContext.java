package com.redtour.business.common;

import com.redtour.business.entity.SysUser;

/**
 * 当前登录用户上下文（基于 ThreadLocal）
 * 由 AuthInterceptor 在请求进入时设置，响应完成后清除
 * 业务层任何位置可通过 UserContext.getCurrentUser() 获取当前用户
 */
public class UserContext {

    private static final ThreadLocal<SysUser> HOLDER = new ThreadLocal<>();

    /** 设置当前用户（拦截器调用） */
    public static void set(SysUser user) {
        HOLDER.set(user);
    }

    /** 获取当前用户（未登录返回 null） */
    public static SysUser getCurrentUser() {
        return HOLDER.get();
    }

    /** 获取当前用户 ID（未登录抛 401） */
    public static Long getCurrentUserId() {
        SysUser user = HOLDER.get();
        if (user == null) {
            throw new com.redtour.business.exception.BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user.getId();
    }

    /** 清除上下文（拦截器 finally 中调用，避免线程池复用时串用户） */
    public static void clear() {
        HOLDER.remove();
    }
}
