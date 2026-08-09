package com.redtour.business.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体（对应 sys_user 表）
 * 鉴权骨架与用户管理共用
 */
@Data
public class SysUser {

    /** 主键 */
    private Long id;

    /** 用户名（字母数字下划线 3-20字符） */
    private String username;

    /** BCrypt 加密密码（接口返回时忽略，避免泄露） */
    @JsonIgnore
    private String password;

    /** 昵称 */
    private String nickname;

    /** 角色: super_admin / admin / tourist */
    private String role;

    /** 管辖景区 ID（普管必填） */
    private Long scenicAreaId;

    /** 头像 URL */
    private String avatar;

    /** 状态: 0 禁用 / 1 启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 软删除: 0 未删 / 1 已删 */
    private Integer deleted;
}
