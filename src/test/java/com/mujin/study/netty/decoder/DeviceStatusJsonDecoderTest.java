package com.mujin.study.netty.decoder;

import com.mujin.study.netty.device.DeviceStatus;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeviceStatusJsonDecoderTest {

    @Test
    void decodesJsonStringToDeviceStatus() {
        EmbeddedChannel channel = new EmbeddedChannel(new DeviceStatusJsonDecoder());

        channel.writeInbound("""
                {"deviceId":"device-1","temperature":25.1,"humidity":40.2,"timestamp":1717830000}
                """);

        DeviceStatus status = channel.readInbound();

        assertEquals("device-1", status.deviceId());
        assertEquals(25.1, status.temperature());
        assertEquals(40.2, status.humidity());
        assertEquals(1717830000L, status.timestamp());
        assertNull(channel.readInbound());
    }

    @Test
    void ignoresBlankMessage() {
        EmbeddedChannel channel = new EmbeddedChannel(new DeviceStatusJsonDecoder());

        channel.writeInbound("   ");

        assertNull(channel.readInbound());
    }

    @Test
    void throwsDecoderExceptionWhenJsonIsInvalid() {
        EmbeddedChannel channel = new EmbeddedChannel(new DeviceStatusJsonDecoder());

        assertThrows(DecoderException.class, () -> channel.writeInbound("not-json"));
    }
}
