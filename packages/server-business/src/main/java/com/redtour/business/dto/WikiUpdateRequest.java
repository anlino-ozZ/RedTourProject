package com.redtour.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Wiki 条目编辑请求。
 */
@Data
public class WikiUpdateRequest {

    @NotBlank(message = "Wiki标题不能为空")
    @Size(max = 100, message = "Wiki标题长度不能超过100个字符")
    private String title;

    @NotBlank(message = "Wiki正文不能为空")
    private String content;

    @Size(max = 50, message = "Wiki标签不能超过50个")
    private List<@NotBlank(message = "Wiki标签不能为空") @Size(max = 50, message = "Wiki标签长度不能超过50个字符") String> tags;
}
