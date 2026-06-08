package com.mujin.study.netty.device;

public record DeviceStatus(
        String deviceId,
        double temperature,
        double humidity,
        long timestamp
) {
}
