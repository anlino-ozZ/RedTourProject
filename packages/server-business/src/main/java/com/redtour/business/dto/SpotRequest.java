package com.redtour.business.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 景点新增/编辑请求。
 */
@Data
public class SpotRequest {

    /** 归属景区 ID：admin 由服务端强制为本人景区；super 创建时必填 */
    @Positive(message = "归属景区ID必须为正整数")
    private Long scenicAreaId;

    @NotBlank(message = "景点名称不能为空")
    @Size(max = 50, message = "景点名称长度不能超过50个字符")
    private String name;

    @NotBlank(message = "景点简介不能为空")
    @Size(max = 2000, message = "景点简介过长")
    private String intro;

    @Size(max = 2000, message = "图片数据过长")
    private String images;

    private BigDecimal longitude;

    private BigDecimal latitude;

    @Size(max = 255, message = "音频URL长度不能超过255个字符")
    private String audioUrl;

    @Size(max = 255, message = "Wiki引用长度不能超过255个字符")
    private String wikiRef;

    private Integer sortOrder;

    @Min(0)
    @Max(1)
    private Integer status;
}
