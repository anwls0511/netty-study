package com.mujin.study.netty.service;

import com.mujin.study.netty.device.DeviceStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceStatusServiceTest {

    @Test
    void saveLatestDeviceStatus() {
        DeviceStatusService service = new DeviceStatusService();
        DeviceStatus status = new DeviceStatus("device-1", 25.1, 40.2, 1717830000L);

        service.save(status);

        assertEquals(status, service.findByDeviceId("device-1"));
    }
}
