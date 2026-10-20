package com.redtour.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 离线同步：单条打卡记录。
 * 注：checkin 表仅持久化 spotId / scenicAreaId / clientId / createdAt 等字段，
 * longitude / latitude 为设备端冗余信息，此处接收但不落库。
 */
@Data
public class CheckinSyncItem {

    @NotNull(message = "景点ID不能为空")
    private Long spotId;

    private BigDecimal longitude;

    private BigDecimal latitude;

    @NotNull(message = "景区ID不能为空")
    private Long scenicAreaId;

    @NotBlank(message = "clientId不能为空")
    private String clientId;

    /** 离线端采集时间（可空，空则使用服务端当前时间） */
    private LocalDateTime createdAt;
}
