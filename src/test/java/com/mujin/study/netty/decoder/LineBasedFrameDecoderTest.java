package com.mujin.study.netty.decoder;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.string.StringDecoder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LineBasedFrameDecoderTest {

    @Test
    void waitsUntilLineDelimiterArrives() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new LineBasedFrameDecoder(1024),
                new StringDecoder(StandardCharsets.UTF_8)
        );

        channel.writeInbound(Unpooled.copiedBuffer("{\"deviceId\":\"device-1\",", StandardCharsets.UTF_8));

        assertNull(channel.readInbound());

        channel.writeInbound(Unpooled.copiedBuffer("\"temperature\":25.1}\n", StandardCharsets.UTF_8));

        assertEquals("{\"deviceId\":\"device-1\",\"temperature\":25.1}", channel.readInbound());
    }

    @Test
    void separatesMultipleMessagesInOneRead() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new LineBasedFrameDecoder(1024),
                new StringDecoder(StandardCharsets.UTF_8)
        );

        channel.writeInbound(Unpooled.copiedBuffer("""
                {"deviceId":"device-1","temperature":25.1}
                {"deviceId":"device-2","temperature":26.2}
                """, StandardCharsets.UTF_8));

        assertEquals("{\"deviceId\":\"device-1\",\"temperature\":25.1}", channel.readInbound());
        assertEquals("{\"deviceId\":\"device-2\",\"temperature\":26.2}", channel.readInbound());
        assertNull(channel.readInbound());
    }

    @Test
    void removesCarriageReturnAndLineFeedDelimiter() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new LineBasedFrameDecoder(1024),
                new StringDecoder(StandardCharsets.UTF_8)
        );

        channel.writeInbound(Unpooled.copiedBuffer("PING\r\n", StandardCharsets.UTF_8));

        assertEquals("PING", channel.readInbound());
        assertNull(channel.readInbound());
    }

    @Test
    void throwsWhenLineIsLongerThanMaxFrameLength() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new LineBasedFrameDecoder(8),
                new StringDecoder(StandardCharsets.UTF_8)
        );

        assertThrows(TooLongFrameException.class, () ->
                channel.writeInbound(Unpooled.copiedBuffer("too-long-message\n", StandardCharsets.UTF_8))
        );
    }
}
