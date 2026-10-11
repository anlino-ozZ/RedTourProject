package com.redtour.business.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 发往 AI 引擎 Wiki 编译接口的请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WikiCompileRequest {

    private Long scenicAreaId;
    private String title;
    private String content;
    private List<String> tags;
}
