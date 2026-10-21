package com.redtour.business.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redtour.business.client.HardwareTcpClient;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.DeviceAudioPlayRequest;
import com.redtour.business.dto.DeviceCommandResult;
import com.redtour.business.dto.DeviceHeartbeatRequest;
import com.redtour.business.dto.DeviceHeartbeatResult;
import com.redtour.business.dto.DeviceStatusResult;
import com.redtour.business.dto.DeviceVolumeRequest;
import com.redtour.business.entity.DeviceStatus;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.DeviceStatusMapper;
import com.redtour.business.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final DeviceStatusMapper deviceStatusMapper;
    private final HardwareTcpClient hardwareTcpClient;
    private final ObjectMapper objectMapper;

    @Override
    public DeviceHeartbeatResult handleHeartbeat(DeviceHeartbeatRequest request) {
        DeviceStatus status = new DeviceStatus();
        status.setDeviceId(request.getDeviceId());
        status.setScenicAreaId(request.getScenicAreaId());
        status.setCpuTemp(request.getCpuTemp());
        status.setMemoryUsage(request.getMemoryUsage());
        status.setDiskUsage(request.getDiskUsage());
        status.setHailoStatus(request.getHailoStatus());
        status.setCameraConnected(request.getCameraConnected());
        status.setSpeakerConnected(request.getSpeakerConnected());
        deviceStatusMapper.upsert(status);
        DeviceHeartbeatResult result = new DeviceHeartbeatResult();
        result.setOnline(Boolean.TRUE);
        result.setLastHeartbeat(LocalDateTime.now());
        return result;
    }

    @Override
    public List<DeviceStatusResult> listStatus(Long scenicAreaId) {
        Long scopedAreaId = scopeScenicAreaId(scenicAreaId);
        List<DeviceStatus> rows = deviceStatusMapper.findByScenicAreaId(scopedAreaId);
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(this::toResult).collect(Collectors.toList());
    }

    @Override
    public void reboot(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "设备ID不能为空");
        }
        String cmd = String.format("{\"cmd\":\"reboot\",\"deviceId\":\"%s\"}", deviceId);
        try {
            hardwareTcpClient.sendCommand(cmd);
        } catch (Exception e) {
            log.error("[设备] 下发重启指令失败 deviceId={} cause={}", deviceId, e.getMessage());
            throw new BusinessException(ResultCode.HARDWARE_ERROR, "重启指令下发失败: " + e.getMessage());
        }
    }

    @Override
    public DeviceCommandResult playAudio(String deviceId, DeviceAudioPlayRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cmd", "play_audio");
        payload.put("deviceId", deviceId);
        payload.put("audioUrl", request.getAudioUrl());
        return dispatch(deviceId, "play_audio", payload);
    }

    @Override
    public DeviceCommandResult stopAudio(String deviceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cmd", "stop_audio");
        payload.put("deviceId", deviceId);
        return dispatch(deviceId, "stop_audio", payload);
    }

    @Override
    public DeviceCommandResult setVolume(String deviceId, DeviceVolumeRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cmd", "set_volume");
        payload.put("deviceId", deviceId);
        payload.put("volume", request.getVolume());
        return dispatch(deviceId, "set_volume", payload);
    }

    /**
     * 统一下发硬件指令：序列化 JSON → TCP 发送 → 校验硬件响应 status。
     * 硬件侧协议见 packages/hardware-rpi/drivers/tcp_server.py。
     */
    private DeviceCommandResult dispatch(String deviceId, String command, Map<String, Object> payload) {
        String cmdJson;
        try {
            cmdJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "指令序列化失败: " + e.getMessage());
        }

        String resp;
        try {
            resp = hardwareTcpClient.sendCommand(cmdJson);
        } catch (Exception e) {
            log.error("[设备] 下发指令失败 deviceId={} cmd={} cause={}", deviceId, command, e.getMessage());
            throw new BusinessException(ResultCode.HARDWARE_ERROR, "指令下发失败: " + e.getMessage());
        }

        if (!"ok".equals(readStatus(resp))) {
            log.warn("[设备] 硬件返回异常 deviceId={} cmd={} resp={}", deviceId, command, resp);
            throw new BusinessException(ResultCode.HARDWARE_ERROR, "硬件执行失败: " + resp);
        }

        DeviceCommandResult result = new DeviceCommandResult();
        result.setDeviceId(deviceId);
        result.setCommand(command);
        result.setSent(Boolean.TRUE);
        result.setResponse(resp);
        return result;
    }

    /** 从硬件响应中取 status 字段，解析失败返回空串 */
    private String readStatus(String resp) {
        if (resp == null) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(resp);
            return node.path("status").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private DeviceStatusResult toResult(DeviceStatus s) {
        DeviceStatusResult r = new DeviceStatusResult();
        r.setDeviceId(s.getDeviceId());
        r.setScenicAreaId(s.getScenicAreaId());
        r.setOnline(s.getOnline());
        r.setCpuTemp(s.getCpuTemp());
        r.setMemoryUsage(s.getMemoryUsage());
        r.setDiskUsage(s.getDiskUsage());
        r.setHailoStatus(s.getHailoStatus());
        r.setCameraConnected(s.getCameraConnected());
        r.setSpeakerConnected(s.getSpeakerConnected());
        r.setLastHeartbeat(s.getLastHeartbeat());
        return r;
    }

    /** admin 强制本人景区；super 透传筛选条件（可 null 查全量）。 */
    private Long scopeScenicAreaId(Long scenicAreaId) {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if ("super_admin".equals(current.getRole())) {
            return scenicAreaId;
        }
        if (!"admin".equals(current.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (current.getScenicAreaId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前管理员未分配景区");
        }
        return current.getScenicAreaId();
    }
}
