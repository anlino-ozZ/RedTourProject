package com.redtour.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 离线同步：单条问答记录。
 */
@Data
public class AskLogSyncItem {

    @NotBlank(message = "问题不能为空")
    private String question;

    private String answer;

    @NotNull(message = "景区ID不能为空")
    private Long scenicAreaId;

    @NotBlank(message = "clientId不能为空")
    private String clientId;

    /** 离线端采集时间（可空，空则使用服务端当前时间） */
    private LocalDateTime createdAt;
}
