package com.mujin.study.netty.handler;

import com.mujin.study.netty.device.DeviceStatus;
import com.mujin.study.netty.service.DeviceStatusService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class DeviceStatusHandler extends SimpleChannelInboundHandler<DeviceStatus> {

    private final DeviceStatusService deviceStatusService;

    public DeviceStatusHandler(DeviceStatusService deviceStatusService) {
        this.deviceStatusService = deviceStatusService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DeviceStatus msg) {
        deviceStatusService.save(msg);
        ctx.writeAndFlush("OK\n");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("Failed to handle device message: " + cause.getMessage());
        ctx.writeAndFlush("ERROR\n");
        ctx.close();
    }
}
