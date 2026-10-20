package com.redtour.business.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 剧本新增/编辑请求。
 * 注意：实体字段为 title（非 name），roles/nodes/achievements 均为 JSON 字符串。
 */
@Data
public class ScriptRequest {

    /** 归属景区 ID：super 必填；admin 由服务端强制本人景区，可不传 */
    private Long scenicAreaId;

    @NotBlank(message = "剧本名称不能为空")
    @Size(max = 100, message = "剧本名称长度不能超过100个字符")
    private String title;

    @Size(max = 2000, message = "剧本简介过长")
    private String description;

    @Size(max = 255, message = "封面图URL长度不能超过255个字符")
    private String coverImage;

    /** 角色列表 JSON 字符串 [{id,name,avatar,skill}] */
    private String roles;

    /** 剧情节点树 JSON 字符串 [{id,sceneText,options:[{text,nextNodeId}]}] */
    private String nodes;

    /** 起始节点 ID */
    private Integer startNodeId;

    /** 可解锁成就 ID 列表 JSON 字符串 */
    private String achievements;

    @NotNull(message = "状态不能为空")
    @Min(0)
    @Max(1)
    private Integer status;
}
