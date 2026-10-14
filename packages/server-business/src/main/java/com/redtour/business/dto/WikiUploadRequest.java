package com.redtour.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Wiki 素材上传请求。
 */
@Data
public class WikiUploadRequest {

    @NotNull(message = "景区ID不能为空")
    @Positive(message = "景区ID必须为正整数")
    private Long scenicAreaId;

    @NotBlank(message = "Wiki标题不能为空")
    @Size(max = 100, message = "Wiki标题长度不能超过100个字符")
    private String title;

    @NotBlank(message = "Wiki正文不能为空")
    private String content;

    @Size(max = 50, message = "Wiki标签不能超过50个")
    private List<@NotBlank(message = "Wiki标签不能为空") @Size(max = 50, message = "Wiki标签长度不能超过50个字符") String> tags;
}
