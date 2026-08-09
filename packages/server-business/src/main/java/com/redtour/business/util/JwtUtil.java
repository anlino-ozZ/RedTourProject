package com.redtour.business.util;

import com.redtour.business.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（基于 jjwt 0.12.x API）
 * - token 有效期 2 小时（接口文档约定）
 * - 载荷包含 userId / username / role / scenicAreaId
 * - 使用 HS256 对称加密
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret:RedTourProject-default-secret-key-please-change-in-production-123456}")
    private String secret;

    /** 过期毫秒：2 小时 */
    @Value("${jwt.expire-hours:2}")
    private long expireHours;

    private SecretKey signingKey;

    /** 懒加载签名 key（保证 @Value 注入完成后再构造） */
    private SecretKey getKey() {
        if (signingKey == null) {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            signingKey = Keys.hmacShaKeyFor(bytes);
        }
        return signingKey;
    }

    /** 根据用户生成 token */
    public String generate(SysUser user) {
        long now = System.currentTimeMillis();
        long expMillis = expireHours * 60L * 60L * 1000L;
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .claim("scenicAreaId", user.getScenicAreaId())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expMillis))
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析 token，返回 Claims（失败返回 null）
     * 包含过期校验、签名校验，任何一步失败即判定 token 无效
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            // 签名错误 / 过期 / 格式错误 都判定为无效
            return null;
        }
    }

    /** 从 claims 中取出 userId */
    public Long extractUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }
}
