package com.redtour.business.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 成就定义新增/编辑请求。
 * 备注：brief B5 中描述的 name 对应实际 schema 的 title 列；triggerType 对应 trigger_type。
 */
@Data
public class AchievementRequest {

    @NotBlank(message = "成就标题不能为空")
    @Size(max = 50, message = "成就标题长度不能超过50个字符")
    private String title;

    @Size(max = 255, message = "图标URL长度不能超过255个字符")
    private String icon;

    @Size(max = 200, message = "成就描述过长")
    private String description;

    @NotBlank(message = "触发类型不能为空")
    @Size(max = 20, message = "触发类型长度不合法")
    private String triggerType;

    /** 触发条件配置（JSON 字符串）。 */
    private String triggerCondition;

    @NotNull(message = "状态不能为空")
    @Min(0)
    @Max(1)
    private Integer status;

    /** 归属景区 ID：super_admin 创建时必填指定；admin 无需传（自动用本人景区）。 */
    private Long scenicAreaId;
}
