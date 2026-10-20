package com.redtour.business.dto;

import lombok.Data;

import java.util.List;

/**
 * 剧本完成结果响应。
 */
@Data
public class ScriptCompleteResult {

    private Long scriptId;
    private boolean completed;
    /** 本次解锁的成就标题列表 */
    private List<String> achievements;
}
