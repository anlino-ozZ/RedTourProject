package com.redtour.business.controller;

import com.redtour.business.annotation.RequireRole;
import com.redtour.business.common.ApiResponse;
import com.redtour.business.dto.DeviceAudioPlayRequest;
import com.redtour.business.dto.DeviceCommandResult;
import com.redtour.business.dto.DeviceHeartbeatRequest;
import com.redtour.business.dto.DeviceHeartbeatResult;
import com.redtour.business.dto.DeviceStatusResult;
import com.redtour.business.dto.DeviceVolumeRequest;
import com.redtour.business.service.DeviceService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 设备接口：
 * - POST /api/v1/devices/heartbeat（白名单，无需登录/鉴权）
 * - GET  /api/v1/devices/status   （@RequireRole super_admin,admin，scenicAreaId 隔离）
 * - POST /api/v1/devices/{deviceId}/reboot （@RequireRole super_admin，下发重启指令）
 */
@Validated
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /** 设备心跳上报（局域网白名单，无 auth）。 */
    @PostMapping("/heartbeat")
    public ApiResponse<DeviceHeartbeatResult> heartbeat(@Valid @RequestBody DeviceHeartbeatRequest request) {
        return ApiResponse.success(deviceService.handleHeartbeat(request));
    }

    /** 设备状态列表（admin 强制本人景区，super 可传 null 查全量）。 */
    @GetMapping("/status")
    @RequireRole({"super_admin", "admin"})
    public ApiResponse<List<DeviceStatusResult>> status(
            @RequestParam(required = false) Long scenicAreaId) {
        return ApiResponse.success(deviceService.listStatus(scenicAreaId));
    }

    /** 远程重启指定设备。 */
    @PostMapping("/{deviceId}/reboot")
    @RequireRole("super_admin")
    public ApiResponse<Map<String, Object>> reboot(
            @PathVariable @Pattern(regexp = "^[\\w-]+$", message = "设备ID格式非法") String deviceId) {
        deviceService.reboot(deviceId);
        return ApiResponse.success(Map.of("deviceId", deviceId, "sent", Boolean.TRUE));
    }

    /** 下发播放讲解音频指令。 */
    @PostMapping("/{deviceId}/audio/play")
    @RequireRole({"super_admin", "admin"})
    public ApiResponse<DeviceCommandResult> playAudio(
            @PathVariable @Pattern(regexp = "^[\\w-]+$", message = "设备ID格式非法") String deviceId,
            @Valid @RequestBody DeviceAudioPlayRequest request) {
        return ApiResponse.success(deviceService.playAudio(deviceId, request));
    }

    /** 下发停止播放指令。 */
    @PostMapping("/{deviceId}/audio/stop")
    @RequireRole({"super_admin", "admin"})
    public ApiResponse<DeviceCommandResult> stopAudio(
            @PathVariable @Pattern(regexp = "^[\\w-]+$", message = "设备ID格式非法") String deviceId) {
        return ApiResponse.success(deviceService.stopAudio(deviceId));
    }

    /** 下发音量设置指令（0-100）。 */
    @PostMapping("/{deviceId}/volume")
    @RequireRole({"super_admin", "admin"})
    public ApiResponse<DeviceCommandResult> setVolume(
            @PathVariable @Pattern(regexp = "^[\\w-]+$", message = "设备ID格式非法") String deviceId,
            @Valid @RequestBody DeviceVolumeRequest request) {
        return ApiResponse.success(deviceService.setVolume(deviceId, request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("参数格式错误");
        return ApiResponse.error(400, message);
    }
}
