package com.redtour.business.client;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * 树莓派硬件 TCP 通信客户端
 * 与 packages/hardware-rpi 建立长连接，按行（换行分隔）收发 JSON 指令
 */
@Slf4j
@Component
public class HardwareTcpClient {

    @Value("${hardware.tcp.host}")
    private String host;

    @Value("${hardware.tcp.port}")
    private int port;

    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    /** 建立连接 */
    public synchronized void connect() throws Exception {
        if (isConnected()) {
            return;
        }
        socket = new Socket(host, port);
        writer = new PrintWriter(socket.getOutputStream(), true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        log.info("[硬件] 已连接树莓派 {}:{}", host, port);
    }

    /** 是否已连接 */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * 发送指令并读取一行响应
     * 连接断开时自动重连一次后重试
     */
    public String sendCommand(String jsonCmd) throws Exception {
        if (!isConnected()) {
            connect();
        }
        writer.println(jsonCmd);
        String resp = reader.readLine();
        if (resp == null) {
            close();
            connect();
            writer.println(jsonCmd);
            resp = reader.readLine();
        }
        return resp;
    }

    @PreDestroy
    public void close() {
        try {
            if (reader != null) reader.close();
        } catch (Exception ignored) {
        }
        try {
            if (writer != null) writer.close();
        } catch (Exception ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        socket = null;
        writer = null;
        reader = null;
    }
}
