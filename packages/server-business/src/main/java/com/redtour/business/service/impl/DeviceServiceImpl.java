package com.redtour.business.service.impl;

import com.redtour.business.client.HardwareTcpClient;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.DeviceHeartbeatRequest;
import com.redtour.business.dto.DeviceHeartbeatResult;
import com.redtour.business.dto.DeviceStatusResult;
import com.redtour.business.entity.DeviceStatus;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.DeviceStatusMapper;
import com.redtour.business.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
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
