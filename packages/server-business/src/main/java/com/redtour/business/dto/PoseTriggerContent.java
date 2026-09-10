package com.redtour.business.dto;

import lombok.Data;

/**
 * 姿态动作命中后的互动内容。
 */
@Data
public class PoseTriggerContent {

    private String title;
    private String audioUrl;
    private String wikiRef;
}
