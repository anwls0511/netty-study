package com.mujin.study.netty;

import com.mujin.study.netty.server.NettyTcpServer;
import com.mujin.study.netty.service.DeviceStatusService;

public class NettyStudyApplication {

    public static void main(String[] args) throws InterruptedException {
        int port = 9000;
        DeviceStatusService deviceStatusService = new DeviceStatusService();
        NettyTcpServer server = new NettyTcpServer(port, deviceStatusService);

        server.start();
    }
}
