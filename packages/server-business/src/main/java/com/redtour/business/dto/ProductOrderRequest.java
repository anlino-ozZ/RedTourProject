package com.redtour.business.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 特产意向订单提交请求（游客端，需登录）。
 */
@Data
public class ProductOrderRequest {

    @NotBlank(message = "联系人姓名不能为空")
    @Size(max = 50, message = "联系人姓名过长")
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "联系电话须为11位手机号")
    private String contactPhone;

    @Min(value = 1, message = "数量至少为1")
    @Max(value = 99, message = "数量不能超过99")
    private Integer quantity = 1;

    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark;
}
