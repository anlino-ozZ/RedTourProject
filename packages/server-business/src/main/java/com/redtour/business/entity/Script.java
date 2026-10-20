package com.redtour.business.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 剧本实体，对应 script 表。
 * roles / nodes / achievements 在数据库中以 JSON 字符串保存。
 */
@Data
public class Script {

    /** 主键 */
    private Long id;

    /** 归属景区 ID */
    private Long scenicAreaId;

    /** 剧本名称 */
    private String title;

    /** 剧本简介 */
    private String description;

    /** 封面图 URL */
    private String coverImage;

    /** 角色列表 JSON 字符串 [{id,name,avatar,skill}] */
    private String roles;

    /** 剧情节点树 JSON 字符串 [{id,sceneText,options:[{text,nextNodeId}]}] */
    private String nodes;

    /** 起始节点 ID */
    private Integer startNodeId;

    /** 可解锁成就 ID 列表 JSON 字符串 */
    private String achievements;

    /** 状态: 0 下线 / 1 上线 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}