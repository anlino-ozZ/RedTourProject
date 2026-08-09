package com.redtour.business.service.impl;

import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.LoginRequest;
import com.redtour.business.dto.LoginResult;
import com.redtour.business.dto.RegisterRequest;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.UserMapper;
import com.redtour.business.service.AuthService;
import com.redtour.business.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 鉴权服务实现
 * - BCrypt 校验密码
 * - JWT 生成 / 黑名单（登出时写入 Redis）
 * - 游客注册默认角色 tourist
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    /** BCrypt 加密器（strength=10，符合 PRD 要求） */
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    /** JWT 黑名单 key 前缀 */
    private static final String TOKEN_BLACKLIST_KEY = "jwt:blacklist:";

    public static BCryptPasswordEncoder getPasswordEncoder() {
        return PASSWORD_ENCODER;
    }

    @Override
    public LoginResult login(LoginRequest req) {
        SysUser user = userMapper.findByUsername(req.getUsername());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!PASSWORD_ENCODER.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已禁用");
        }
        String token = jwtUtil.generate(user);
        log.info("[登录] 用户 {} 登录成功，角色 {}", user.getId(), user.getRole());
        return toResult(user, token);
    }

    @Override
    public LoginResult register(RegisterRequest req) {
        // 用户名唯一校验
        if (userMapper.findByUsername(req.getUsername()) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已被占用");
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(PASSWORD_ENCODER.encode(req.getPassword()));
        user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());
        user.setRole("tourist");
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);
        log.info("[注册] 新游客注册成功 id={} username={}", user.getId(), user.getUsername());
        // 注册后直接发 token，免二次登录
        String token = jwtUtil.generate(user);
        return toResult(user, token);
    }

    @Override
    public LoginResult me() {
        SysUser user = UserContext.getCurrentUser();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return toResult(user, null);
    }

    @Override
    public void logout(String token) {
        // 写入 Redis 黑名单，过期时间 = JWT 剩余有效期，这里简单用 2 小时兜底
        try {
            redisTemplate.opsForValue().set(TOKEN_BLACKLIST_KEY + token, "1", 2, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[登出] Redis 不可用，跳过 token 黑名单写入：{}", e.getMessage());
        }
        UserContext.clear();
    }

    private LoginResult toResult(SysUser user, String token) {
        LoginResult r = new LoginResult();
        r.setId(user.getId());
        r.setUsername(user.getUsername());
        r.setNickname(user.getNickname());
        r.setRole(user.getRole());
        r.setAvatar(user.getAvatar());
        r.setScenicAreaId(user.getScenicAreaId());
        r.setToken(token);
        return r;
    }
}
