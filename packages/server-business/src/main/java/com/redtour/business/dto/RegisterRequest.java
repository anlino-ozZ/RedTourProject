package com.redtour.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 游客自助注册请求参数
 * 仅允许 role=tourist（其他角色由超管后台创建）
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名 3-20 字符")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅允许字母数字下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码 6-32 字符")
    private String password;

    @Size(max = 20, message = "昵称最多 20 字符")
    private String nickname;
}
