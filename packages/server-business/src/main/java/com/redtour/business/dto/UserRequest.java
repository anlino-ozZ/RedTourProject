package com.redtour.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户新增/编辑请求。
 */
@Data
public class UserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名 3-20 字符")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅允许字母数字下划线")
    private String username;

    /**
     * 密码：新建时必填，编辑时可选（空则不修改密码）。
     */
    @Size(min = 6, max = 32, message = "密码 6-32 字符")
    private String password;

    @Size(max = 20, message = "昵称最多 20 字符")
    private String nickname;

    /**
     * 角色：super_admin / admin / tourist。
     * 仅 super_admin 可设置 admin 或 super_admin。
     */
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(super_admin|admin|tourist)$", message = "角色仅允许 super_admin / admin / tourist")
    private String role;

    /**
     * 管辖景区ID：admin 角色必填，super_admin 可为空（管辖全局）。
     * tourist 角色忽略此字段。
     */
    private Long scenicAreaId;

    /**
     * 头像URL。
     */
    @Size(max = 255, message = "头像URL过长")
    private String avatar;

    /**
     * 账号状态：0=禁用，1=启用。
     */
    @NotNull(message = "状态不能为空")
    private Integer status;
}
