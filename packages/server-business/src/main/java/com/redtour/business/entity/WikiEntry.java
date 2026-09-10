package com.redtour.business.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Wiki 条目实体，对应 wiki_entry 表。
 * tags/links 在数据库中以 JSON 字符串保存，由业务层转换为列表。
 */
@Data
public class WikiEntry {

    private Long id;
    private Long scenicAreaId;
    private String title;
    private String filePath;
    private String content;
    private String tags;
    private String links;
    private String compileStatus;
    private LocalDateTime updatedAt;
}
