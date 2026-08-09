package com.redtour.business.aspect;

import com.redtour.business.annotation.RequireRole;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 角色权限校验切面
 * 拦截加了 @RequireRole 的 Controller 类/方法：
 *   - 先取类上的注解，再取方法上的注解，方法级优先级高
 *   - 当前用户 role 不在允许列表内，直接抛 403
 *   - 普管数据隔离（scenicAreaId 校验）放在具体 Service 层做，因为不同业务规则不同
 */
@Slf4j
@Aspect
@Component
public class RoleAspect {

    @Around("@within(com.redtour.business.annotation.RequireRole) || @annotation(com.redtour.business.annotation.RequireRole)")
    public Object checkRole(ProceedingJoinPoint pjp) throws Throwable {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        // 方法级优先级高，先找方法，再找类
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        RequireRole methodAnno = AnnotationUtils.findAnnotation(method, RequireRole.class);
        RequireRole classAnno = AnnotationUtils.findAnnotation(pjp.getTarget().getClass(), RequireRole.class);
        RequireRole anno = methodAnno != null ? methodAnno : classAnno;
        if (anno == null) {
            return pjp.proceed();
        }
        String userRole = current.getRole();
        boolean allowed = Arrays.asList(anno.value()).contains(userRole);
        if (!allowed) {
            log.warn("[权限] 用户 {} 角色 {} 不匹配允许列表 {}", current.getId(), userRole, Arrays.toString(anno.value()));
            // 超管放行一切（兜底策略，避免注解写漏超管导致超管自己进不去）
            if (!"super_admin".equals(userRole)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "角色权限不足");
            }
        }
        return pjp.proceed();
    }
}
