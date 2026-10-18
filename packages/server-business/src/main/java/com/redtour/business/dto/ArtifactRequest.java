package com.redtour.business.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文物新增/编辑请求。字段以 schema.sql / Artifact 实体为准。
 * 备注：brief B4 中描述的 description / image 对应实际 schema 的 intro（简介, NOT NULL）/ images（JSON 数组）。
 * scenicAreaId 由 super_admin 指定；admin 强制本人景区（服务端解析）。
 */
@Data
public class ArtifactRequest {

    /** 归属景区 ID（super_admin 必填；admin 忽略，强制本人景区） */
    private Long scenicAreaId;

    @NotBlank(message = "文物名称不能为空")
    @Size(max = 100, message = "文物名称长度不能超过100个字符")
    private String name;

    @Size(max = 50, message = "年代长度不能超过50个字符")
    private String era;

    @NotBlank(message = "文物简介不能为空")
    private String intro;

    /** 图片 URL 数组（JSON 字符串）。 */
    private String images;

    @Size(max = 100, message = "出土/收藏地点长度不能超过100个字符")
    private String location;

    @Size(max = 50, message = "品类长度不能超过50个字符")
    private String category;

    @Size(max = 255, message = "Wiki 条目路径长度不能超过255个字符")
    private String wikiRef;

    @NotNull(message = "状态不能为空")
    @Min(0)
    @Max(1)
    private Integer status;
}
