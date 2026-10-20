package com.redtour.business.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 路线打卡请求（需登录）。
 */
@Data
public class CheckinRequest {

    /** 目标景点 ID，必须在路线 spotIds 范围内 */
    @NotNull(message = "景点ID不能为空")
    private Long spotId;

    /** 当前经度 */
    @NotNull(message = "经度不能为空")
    private BigDecimal longitude;

    /** 当前纬度 */
    @NotNull(message = "纬度不能为空")
    private BigDecimal latitude;
}
