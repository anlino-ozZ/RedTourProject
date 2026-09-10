package com.redtour.business.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单帧姿态识别结果。
 */
@Data
public class PoseRecognizeResult {

    private String action;
    private Double confidence;
    private List<List<Double>> keypoints = new ArrayList<>();
    private PoseTriggerContent triggerContent;
    private Long timestamp;
}
