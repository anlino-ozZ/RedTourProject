package com.redtour.business.service;

import com.redtour.business.dto.LoginRequest;
import com.redtour.business.dto.LoginResult;
import com.redtour.business.dto.RegisterRequest;

/**
 * 鉴权服务接口
 */
public interface AuthService {

    /** 登录：校验用户名密码 → 生成 JWT */
    LoginResult login(LoginRequest req);

    /** 游客自助注册：默认 role=tourist */
    LoginResult register(RegisterRequest req);

    /** 获取当前登录用户信息（/auth/me 使用） */
    LoginResult me();

    /** 登出：后端 token 加入 Redis 黑名单（可选实现，先清空本地上下文） */
    void logout(String token);
}
