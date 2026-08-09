package com.redtour.business.interceptor;

import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.UserMapper;
import com.redtour.business.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器
 * - 从 Authorization: Bearer <token> 提取并校验 token
 * - 将用户对象存入 UserContext（ThreadLocal），业务层可直接取
 * - 响应结束后 clear 避免线程池复用串用户
 * 白名单路径（登录/健康检查/公开接口）在 WebMvcConfig 中配置 excludePathPatterns
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少 Authorization 头");
        }
        String token = header.substring(7);
        Claims claims = jwtUtil.parse(token);
        if (claims == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Token 无效或已过期");
        }
        Long userId = jwtUtil.extractUserId(claims);
        SysUser user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已禁用");
        }
        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
