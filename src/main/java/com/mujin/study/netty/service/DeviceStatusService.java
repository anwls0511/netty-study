package com.mujin.study.netty.service;

import com.mujin.study.netty.device.DeviceStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceStatusService {

    private final Map<String, DeviceStatus> latestStatuses = new ConcurrentHashMap<>();

    public void save(DeviceStatus status) {
        latestStatuses.put(status.deviceId(), status);
        System.out.println("Saved device status: " + status);
    }

    public DeviceStatus findByDeviceId(String deviceId) {
        return latestStatuses.get(deviceId);
    }
}
