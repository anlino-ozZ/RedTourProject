package com.redtour.business.config;

import com.redtour.business.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 JWT 鉴权拦截器
 * - 白名单：登录、健康检查、所有公开接口（游客端浏览/触摸屏公开接口）
 * - 管理端 /admin/**  和 游客端需要登录的接口，由拦截器校验
 * 注意：公开接口具体路径以接口文档为准，团队可在此处继续追加 excludePathPatterns
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        // 登录 + 健康检查
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/health",
                        // 景区 / 景点 / 路线 / 文物 / 特产 / 剧本 / 推荐问题 公开浏览
                        "/api/v1/scenic-areas/**",
                        "/api/v1/spots/**",
                        "/api/v1/routes/**",
                        "/api/v1/products/**",
                        "/api/v1/artifacts/**",
                        "/api/v1/scripts/**",
                        "/api/v1/ask/recommendations",
                        "/api/v1/ask",
                        "/api/v1/stt",
                        // 姿态识别公开接口
                        "/api/v1/pose/**",
                        // 设备心跳（局域网白名单校验另做）
                        "/api/v1/devices/heartbeat",
                        // 文件访问
                        "/uploads/**",
                        "/audio/**",
                        "/icons/**"
                );
    }
}
