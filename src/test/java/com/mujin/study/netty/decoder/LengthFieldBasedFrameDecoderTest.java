package com.mujin.study.netty.decoder;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.string.StringDecoder;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LengthFieldBasedFrameDecoderTest {

    @Test
    void waitsUntilBodyLengthIsSatisfied() {
        EmbeddedChannel channel = newLengthFieldChannel();
        byte[] frame = frame("""
                {"deviceId":"device-1","temperature":25.1}
                """.trim());

        channel.writeInbound(Unpooled.copiedBuffer(frame, 0, 10));

        assertNull(channel.readInbound());

        channel.writeInbound(Unpooled.copiedBuffer(frame, 10, frame.length - 10));

        assertEquals("{\"deviceId\":\"device-1\",\"temperature\":25.1}", channel.readInbound());
        assertNull(channel.readInbound());
    }

    @Test
    void separatesMultipleLengthBasedFramesInOneRead() {
        EmbeddedChannel channel = newLengthFieldChannel();

        byte[] first = frame("{\"deviceId\":\"device-1\",\"temperature\":25.1}");
        byte[] second = frame("{\"deviceId\":\"device-2\",\"temperature\":26.2}");

        channel.writeInbound(Unpooled.wrappedBuffer(first, second));

        assertEquals("{\"deviceId\":\"device-1\",\"temperature\":25.1}", channel.readInbound());
        assertEquals("{\"deviceId\":\"device-2\",\"temperature\":26.2}", channel.readInbound());
        assertNull(channel.readInbound());
    }

    @Test
    void waitsUntilFullLengthHeaderArrives() {
        EmbeddedChannel channel = newLengthFieldChannel();
        byte[] frame = frame("{\"deviceId\":\"device-1\"}");

        channel.writeInbound(Unpooled.copiedBuffer(frame, 0, 2));

        assertNull(channel.readInbound());

        channel.writeInbound(Unpooled.copiedBuffer(frame, 2, frame.length - 2));

        assertEquals("{\"deviceId\":\"device-1\"}", channel.readInbound());
        assertNull(channel.readInbound());
    }

    @Test
    void throwsWhenLengthFieldExceedsMaxFrameLength() {
        EmbeddedChannel channel = newLengthFieldChannel();
        byte[] headerOnly = ByteBuffer.allocate(4)
                .putInt(2048)
                .array();

        assertThrows(TooLongFrameException.class, () ->
                channel.writeInbound(Unpooled.wrappedBuffer(headerOnly))
        );
    }

    private EmbeddedChannel newLengthFieldChannel() {
        return new EmbeddedChannel(
                new LengthFieldBasedFrameDecoder(
                        1024,
                        0,
                        4,
                        0,
                        4
                ),
                new StringDecoder(StandardCharsets.UTF_8)
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
