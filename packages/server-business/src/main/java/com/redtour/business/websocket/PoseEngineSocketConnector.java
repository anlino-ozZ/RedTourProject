package com.redtour.business.websocket;

import java.net.URI;
import java.net.http.WebSocket;
import java.util.function.Consumer;

/**
 * 业务后端到 AI 引擎的 WebSocket 连接边界，便于代理测试和资源回收。
 */
public interface PoseEngineSocketConnector {

    WebSocket connect(
            URI uri,
            Consumer<String> onMessage,
            Consumer<Throwable> onError,
            Runnable onClose) throws Exception;
}
