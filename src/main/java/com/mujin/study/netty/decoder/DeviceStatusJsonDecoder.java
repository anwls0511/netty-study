package com.mujin.study.netty.decoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mujin.study.netty.device.DeviceStatus;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

public class DeviceStatusJsonDecoder extends MessageToMessageDecoder<String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void decode(ChannelHandlerContext ctx, String msg, List<Object> out) throws Exception {
        String trimmed = msg.trim();

        if (trimmed.isEmpty()) {
            return;
        }

        DeviceStatus status = objectMapper.readValue(trimmed, DeviceStatus.class);
        validate(status);

        out.add(status);
    }

    private void validate(DeviceStatus status) {
        if (status.deviceId() == null || status.deviceId().isBlank()) {
            throw new IllegalArgumentException("deviceId is required");
        }

        if (status.humidity() < 0 || status.humidity() > 100) {
            throw new IllegalArgumentException("humidity must be between 0 and 100");
        }

        if (status.timestamp() <= 0) {
            throw new IllegalArgumentException("timestamp must be positive");
        }
    }
}
