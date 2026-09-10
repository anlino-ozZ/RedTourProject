package com.redtour.business.controller;

import com.redtour.business.common.ApiResponse;
import com.redtour.business.dto.SttResult;
import com.redtour.business.service.SttService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 语音识别公开代理，前端无需直连 AI 引擎。
 */
@RestController
@RequestMapping("/api/v1/stt")
@RequiredArgsConstructor
public class SttController {

    private final SttService sttService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SttResult> transcribe(
            @RequestPart(value = "audio", required = false) MultipartFile audio) {
        return ApiResponse.success(sttService.transcribe(audio));
    }
}
