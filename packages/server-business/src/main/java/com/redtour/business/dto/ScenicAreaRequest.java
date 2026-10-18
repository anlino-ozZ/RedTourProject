package com.redtour.business.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 景区新增/编辑请求。
 */
@Data
public class ScenicAreaRequest {

    @NotBlank(message = "景区名称不能为空")
    @Size(max = 50, message = "景区名称长度不能超过50个字符")
    private String name;

    @Size(max = 2000, message = "景区简介过长")
    private String intro;

    private BigDecimal longitude;

    private BigDecimal latitude;

    @Size(max = 200, message = "详细地址长度不能超过200个字符")
    private String address;

    @Size(max = 50, message = "开放时间长度不能超过50个字符")
    private String openHours;

    @Size(max = 255, message = "封面图URL长度不能超过255个字符")
    private String coverImage;

    @Size(max = 50, message = "所属地区长度不能超过50个字符")
    private String region;

    @Size(max = 20, message = "联系电话长度不能超过20个字符")
    private String phone;

    private Integer sortOrder;

    @NotNull(message = "状态不能为空")
    @Min(0)
    @Max(1)
    private Integer status;
}
