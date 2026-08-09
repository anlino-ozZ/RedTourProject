package com.redtour.business.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 登录 / 注册 成功响应（含 token + 脱敏用户信息）
 * 与接口文档 POST /auth/login 响应对齐
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResult {

    private Long id;
    private String username;
    private String nickname;
    private String role;
    private String avatar;
    private Long scenicAreaId;
    private String token;
}
