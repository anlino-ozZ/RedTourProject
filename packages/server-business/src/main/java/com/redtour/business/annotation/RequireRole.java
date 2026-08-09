package com.redtour.business.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色权限校验注解
 * 加在 Controller 类或方法上，指定允许访问的角色
 * 由 RoleAspect 切面拦截校验，校验失败抛 403
 * 使用示例：
 *   @RequireRole("super_admin")
 *   @RequireRole({"super_admin", "admin"})
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许的角色列表（任一匹配即可通过） */
    String[] value();
}
