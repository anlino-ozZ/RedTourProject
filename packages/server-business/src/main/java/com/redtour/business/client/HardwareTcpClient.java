package com.redtour.business.client;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * 树莓派硬件 TCP 通信客户端
 * - 启动时主动建立连接
 * - 周期性心跳保活 + 断线自动重连（每 5s 检查一次，失败无限重试）
 * - 按行（换行分隔）收发 JSON 指令
 * - 发送命令遇到连接异常自动重连 1 次后重试
 */
@Slf4j
@Component
public class HardwareTcpClient {

    @Value("${hardware.tcp.host}")
    private String host;

    @Value("${hardware.tcp.port}")
    private int port;

    /** SO_TIMEOUT：readLine 阻塞超时时长（毫秒），避免心跳线程卡死 */
    private static final int SO_TIMEOUT_MS = 5000;

    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    /** 启动时首次建连接，失败不阻塞应用启动，交给定时重连任务兜底 */
    @PostConstruct
    public void init() {
        try {
            connect();
        } catch (Exception e) {
            log.warn("[硬件] 启动时连接树莓派失败 ({}:{})，稍后将自动重试: {}",
                    host, port, e.getMessage());
        }
    }

    /** 建立连接（可重入，已连接则直接返回） */
    public synchronized void connect() throws Exception {
        if (isConnected()) {
            return;
        }
        closeQuietly();
        socket = new Socket(host, port);
        socket.setSoTimeout(SO_TIMEOUT_MS);
        socket.setKeepAlive(true);
        writer = new PrintWriter(socket.getOutputStream(), true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        log.info("[硬件] 已连接树莓派 {}:{}", host, port);
    }

    /** 是否已建立可用连接 */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * 心跳 & 自动重连：每 5s 执行一次
     * - 若未连接：尝试建连
     * - 若已连接：发送 ping，收不到 pong 则关闭并触发下一次重连
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 3000)
    public synchronized void heartbeatAndReconnect() {
        try {
            if (!isConnected()) {
                connect();
                return;
            }
            writer.println("{\"cmd\":\"ping\"}");
            String resp = reader.readLine();
            if (resp == null) {
                log.warn("[硬件] 心跳收到 EOF，连接已断开，下次重连...");
                closeQuietly();
            }
        } catch (SocketTimeoutException e) {
            // SO_TIMEOUT 到期：读阻塞超时但链路可能仍在，不强制断开
            log.trace("[硬件] 心跳读超时（{}ms），下次继续", SO_TIMEOUT_MS);
        } catch (Exception e) {
            log.warn("[硬件] 心跳失败，标记断开等待重连: {}", e.getMessage());
            closeQuietly();
        }
    }

    /**
     * 发送指令并读取一行响应
     * 连接断开时自动重连一次后重试；仍失败则向上抛异常
     */
    public String sendCommand(String jsonCmd) throws Exception {
        if (!isConnected()) {
            connect();
        }
        try {
            writer.println(jsonCmd);
            String resp = reader.readLine();
            if (resp == null) {
                // 远端关闭
                closeQuietly();
                connect();
                writer.println(jsonCmd);
                resp = reader.readLine();
            }
            return resp;
        } catch (Exception e) {
            // 发送/读取过程中任何异常：断开后重连重试一次
            log.warn("[硬件] sendCommand 首次失败，重连后重试: {}", e.getMessage());
            closeQuietly();
            connect();
            writer.println(jsonCmd);
            return reader.readLine();
        }
    }

    private void closeQuietly() {
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

    @PreDestroy
    public void close() {
        log.info("[硬件] 服务关闭，释放 TCP 连接");
        closeQuietly();
    }
}
