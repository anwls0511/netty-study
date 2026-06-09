package com.mujin.study.netty.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class BoundaryExperimentClient {

    private static final String HOST = "localhost";
    private static final int PORT = 9000;

    public static void main(String[] args) throws IOException, InterruptedException {
        try (Socket socket = new Socket(HOST, PORT);
             OutputStream out = socket.getOutputStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            sendCompleteMessage(out, reader);
            sendSplitMessage(out, reader);
            sendStickyMessages(out, reader);
        }
    }

    private static void sendCompleteMessage(OutputStream out, BufferedReader reader) throws IOException {
        String message = """
                {"deviceId":"device-1","temperature":25.1,"humidity":40.2,"timestamp":1717830000}
                """;

        System.out.println("[case 1] send complete message");
        write(out, message);
        printResponse(reader);
    }

    private static void sendSplitMessage(OutputStream out, BufferedReader reader) throws IOException, InterruptedException {
        String part1 = "{\"deviceId\":\"device-2\",\"temperature\":26.3,";
        String part2 = "\"humidity\":41.0,\"timestamp\":1717830001}\n";

        System.out.println("[case 2] send one message split into two writes");
        write(out, part1);
        Thread.sleep(500);
        write(out, part2);
        printResponse(reader);
    }

    private static void sendStickyMessages(OutputStream out, BufferedReader reader) throws IOException {
        String messages = """
                {"deviceId":"device-3","temperature":27.0,"humidity":42.5,"timestamp":1717830002}
                {"deviceId":"device-4","temperature":28.4,"humidity":43.1,"timestamp":1717830003}
                """;

        System.out.println("[case 3] send two messages in one write");
        write(out, messages);
        printResponse(reader);
        printResponse(reader);
    }

    private static void write(OutputStream out, String message) throws IOException {
        out.write(message.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void printResponse(BufferedReader reader) throws IOException {
        System.out.println("server response: " + reader.readLine());
    }
}
