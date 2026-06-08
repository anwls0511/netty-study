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

        out.add(objectMapper.readValue(trimmed, DeviceStatus.class));
    }
}
