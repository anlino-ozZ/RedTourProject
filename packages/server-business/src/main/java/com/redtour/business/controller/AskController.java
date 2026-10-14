package com.redtour.business.controller;

import com.redtour.business.common.ApiResponse;
import com.redtour.business.dto.AskRequest;
import com.redtour.business.dto.AskResult;
import com.redtour.business.service.AskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能问答公开接口。
 */
@RestController
@RequestMapping("/api/v1/ask")
@RequiredArgsConstructor
public class AskController {

    private final AskService askService;

    /**
     * 将问题透传给 AI 引擎。触摸屏可通过 X-Device-Id 标记问答来源。
     */
    @PostMapping
    public ApiResponse<AskResult> ask(
            @Valid @RequestBody AskRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        return ApiResponse.success(askService.ask(request, deviceId));
    }
}
