package com.redtour.business.controller;

import com.redtour.business.common.ApiResponse;
import com.redtour.business.dto.PoseRecognizeRequest;
import com.redtour.business.dto.PoseRecognizeResult;
import com.redtour.business.service.PoseRecognizeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 姿态识别公开 HTTP 代理。
 */
@RestController
@RequestMapping("/api/v1/pose")
@RequiredArgsConstructor
public class PoseRecognizeController {

    private final PoseRecognizeService poseRecognizeService;

    @PostMapping("/recognize")
    public ApiResponse<PoseRecognizeResult> recognize(
            @Valid @RequestBody PoseRecognizeRequest request) {
        return ApiResponse.success(poseRecognizeService.recognize(request));
    }
}
