package com.redtour.business.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 打卡点新增/编辑请求。一个景点允许多个打卡点（无唯一约束）。
 */
@Data
public class CheckpointRequest {

    /** 关联景点 ID（必填，用于反查归属景区做权限隔离）。 */
    @NotNull(message = "关联景点ID不能为空")
    private Long spotId;

    private BigDecimal longitude;

    private BigDecimal latitude;

    /** 打卡有效半径（米，默认 50）。 */
    @Min(value = 1, message = "半径必须为正整数")
    private Integer radius;
}
