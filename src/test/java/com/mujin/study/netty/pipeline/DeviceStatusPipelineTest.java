package com.mujin.study.netty.pipeline;

import com.mujin.study.netty.decoder.DeviceStatusJsonDecoder;
import com.mujin.study.netty.handler.DeviceStatusHandler;
import com.mujin.study.netty.service.DeviceStatusService;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeviceStatusPipelineTest {

    @Test
    void waitsForLineDelimiterBeforeSavingDeviceStatus() {
        DeviceStatusService service = new DeviceStatusService();
        EmbeddedChannel channel = newChannel(service);

        channel.writeInbound(Unpooled.copiedBuffer(
                "{\"deviceId\":\"device-1\",\"temperature\":25.1,",
                StandardCharsets.UTF_8
        ));

        assertNull(service.findByDeviceId("device-1"));
        assertNull(channel.readOutbound());

        channel.writeInbound(Unpooled.copiedBuffer(
                "\"humidity\":40.2,\"timestamp\":1717830000}\n",
                StandardCharsets.UTF_8
        ));

        assertEquals("""
                DeviceStatus[deviceId=device-1, temperature=25.1, humidity=40.2, timestamp=1717830000]\
                """, String.valueOf(service.findByDeviceId("device-1")));
        assertEquals("OK\n", channel.readOutbound());
        assertNull(channel.readOutbound());
    }

    @Test
    void savesTwoDeviceStatusesWhenTwoMessagesArriveTogether() {
        DeviceStatusService service = new DeviceStatusService();
        EmbeddedChannel channel = newChannel(service);

        channel.writeInbound(Unpooled.copiedBuffer("""
                {"deviceId":"device-1","temperature":25.1,"humidity":40.2,"timestamp":1717830000}
                {"deviceId":"device-2","temperature":26.3,"humidity":41.0,"timestamp":1717830001}
                """, StandardCharsets.UTF_8));

        assertEquals("""
                DeviceStatus[deviceId=device-1, temperature=25.1, humidity=40.2, timestamp=1717830000]\
                """, String.valueOf(service.findByDeviceId("device-1")));
        assertEquals("""
                DeviceStatus[deviceId=device-2, temperature=26.3, humidity=41.0, timestamp=1717830001]\
                """, String.valueOf(service.findByDeviceId("device-2")));
        assertEquals("OK\n", channel.readOutbound());
        assertEquals("OK\n", channel.readOutbound());
        assertNull(channel.readOutbound());
    }

    private EmbeddedChannel newChannel(DeviceStatusService service) {
        return new EmbeddedChannel(
                new LineBasedFrameDecoder(1024),
                new StringDecoder(StandardCharsets.UTF_8),
                new DeviceStatusJsonDecoder(),
                new DeviceStatusHandler(service)
        );
    }
}
