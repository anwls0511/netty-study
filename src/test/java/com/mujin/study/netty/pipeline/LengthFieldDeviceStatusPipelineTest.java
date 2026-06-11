package com.mujin.study.netty.pipeline;

import com.mujin.study.netty.decoder.DeviceStatusJsonDecoder;
import com.mujin.study.netty.handler.DeviceStatusHandler;
import com.mujin.study.netty.service.DeviceStatusService;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LengthFieldDeviceStatusPipelineTest {

    @Test
    void savesDeviceStatusAfterFullLengthBasedFrameArrives() {
        DeviceStatusService service = new DeviceStatusService();
        EmbeddedChannel channel = newChannel(service);
        byte[] frame = frame("""
                {"deviceId":"device-1","temperature":25.1,"humidity":40.2,"timestamp":1717830000}
                """.trim());

        channel.writeInbound(Unpooled.copiedBuffer(frame, 0, 12));

        assertNull(service.findByDeviceId("device-1"));
        assertNull(channel.readOutbound());

        channel.writeInbound(Unpooled.copiedBuffer(frame, 12, frame.length - 12));

        assertEquals("""
                DeviceStatus[deviceId=device-1, temperature=25.1, humidity=40.2, timestamp=1717830000]\
                """, String.valueOf(service.findByDeviceId("device-1")));
        assertEquals("OK\n", channel.readOutbound());
        assertNull(channel.readOutbound());
    }

    @Test
    void savesTwoDeviceStatusesFromStickyLengthBasedFrames() {
        DeviceStatusService service = new DeviceStatusService();
        EmbeddedChannel channel = newChannel(service);

        byte[] first = frame("""
                {"deviceId":"device-1","temperature":25.1,"humidity":40.2,"timestamp":1717830000}
                """.trim());
        byte[] second = frame("""
                {"deviceId":"device-2","temperature":26.3,"humidity":41.0,"timestamp":1717830001}
                """.trim());

        channel.writeInbound(Unpooled.wrappedBuffer(first, second));

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
                new LengthFieldBasedFrameDecoder(
                        1024,
                        0,
                        4,
                        0,
                        4
                ),
                new StringDecoder(StandardCharsets.UTF_8),
                new DeviceStatusJsonDecoder(),
                new DeviceStatusHandler(service)
        );
    }

    private byte[] frame(String body) {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        return ByteBuffer.allocate(4 + bodyBytes.length)
                .putInt(bodyBytes.length)
                .put(bodyBytes)
                .array();
    }
}
