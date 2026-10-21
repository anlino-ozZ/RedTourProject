package com.redtour.business.config;

import com.redtour.business.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

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

    @Value("${data.upload.path:./uploads}")
    private String uploadPath;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        // 登录 + 健康检查
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/health",
                        // 景区/景点/路线/产品/文物/剧本 的【公开浏览】仅放开列表与详情（单段路径）；
                        // 其下的写接口（下单/打卡/剧本进度/收藏等）为两段路径，不在白名单，自动走 JWT 鉴权。
                        "/api/v1/scenic-areas",
                        "/api/v1/scenic-areas/*",
                        "/api/v1/spots",
                        "/api/v1/spots/*",
                        "/api/v1/routes",
                        "/api/v1/routes/*",
                        "/api/v1/products",
                        "/api/v1/products/*",
                        "/api/v1/artifacts",
                        "/api/v1/artifacts/*",
                        "/api/v1/scripts",
                        "/api/v1/scripts/*",
                        "/api/v1/ask/recommendations",
                        "/api/v1/ask",
                        "/api/v1/stt",
                        // Wiki 公开读取走独立的 /api/v1/wiki（触摸屏快捷信息页数据源）。
                        // 管理端 /api/v1/admin/wiki 已收回到鉴权内，普管按景区隔离。
                        "/api/v1/wiki",
                        "/api/v1/wiki/*",
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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 上传文件落地到本地磁盘，并通过 /uploads/** 对外提供访问
        String location = "file:" + Paths.get(uploadPath).toAbsolutePath().normalize() + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
