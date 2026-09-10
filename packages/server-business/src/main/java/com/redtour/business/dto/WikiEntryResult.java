package com.redtour.business.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Wiki 条目管理端响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WikiEntryResult {

    private Long id;
    private Long scenicAreaId;
    private String title;
    private String filePath;
    private List<String> tags;
    private List<String> links;
    private String compileStatus;
    private LocalDateTime updatedAt;
    private String content;
}
