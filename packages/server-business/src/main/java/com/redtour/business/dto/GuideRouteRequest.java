package com.redtour.business.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 导览路线新增/编辑请求。scenicAreaId 由 super_admin 指定；admin 强制本人景区（服务端解析）。
 */
@Data
public class GuideRouteRequest {

    /** 归属景区 ID（super_admin 必填；admin 忽略，强制本人景区） */
    private Long scenicAreaId;

    @NotBlank(message = "路线名称不能为空")
    @Size(max = 50, message = "路线名称长度不能超过50个字符")
    private String name;

    @Size(max = 200, message = "路线描述过长")
    private String description;

    /** 关联景点 ID 列表（JSON 数组字符串，如 [1,3,5]）。 */
    private String spotIds;

    @NotNull(message = "预计时长不能为空")
    @Min(value = 1, message = "预计时长必须为正整数")
    private Integer duration;

    @NotBlank(message = "难度等级不能为空")
    @Size(max = 10, message = "难度等级长度不合法")
    private String difficulty;

    @NotNull(message = "状态不能为空")
    @Min(0)
    @Max(1)
    private Integer status;
}
