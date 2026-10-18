package com.redtour.business.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 特产/文创产品新增/编辑请求。scenicAreaId 由 super_admin 指定；admin 强制本人景区（服务端解析）。
 */
@Data
public class ProductRequest {

    /** 归属景区 ID（super_admin 必填；admin 忽略，强制本人景区） */
    private Long scenicAreaId;

    @NotBlank(message = "产品名称不能为空")
    @Size(max = 100, message = "产品名称长度不能超过100个字符")
    private String name;

    @NotNull(message = "价格不能为空")
    @Positive(message = "价格必须为正数")
    private BigDecimal price;

    @Size(max = 2000, message = "产品描述过长")
    private String description;

    @Size(max = 255, message = "主图URL长度不能超过255个字符")
    private String image;

    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    @NotBlank(message = "品类不能为空")
    @Size(max = 20, message = "品类长度不合法")
    private String category;

    @NotNull(message = "状态不能为空")
    @Min(0)
    @Max(1)
    private Integer status;
}
