package com.redtour.business.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户响应（不含密码）。
 */
@Data
public class UserResult {

    private Long id;
    private String username;
    private String nickname;
    private String role;
    private Long scenicAreaId;
    private String avatar;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
