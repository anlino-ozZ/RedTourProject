package com.redtour.business.websocket;

import com.redtour.business.config.PoseWebSocketProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 基于 JDK HttpClient 的 AI 引擎 WebSocket 客户端。
 */
@Component
public class JavaNetPoseEngineSocketConnector implements PoseEngineSocketConnector {

    private static final int MAX_ENGINE_RESPONSE_CHARS = 1_000_000;

    private final HttpClient httpClient;
    private final PoseWebSocketProperties properties;

    public JavaNetPoseEngineSocketConnector(PoseWebSocketProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    @Override
    public WebSocket connect(
            URI uri,
            Consumer<String> onMessage,
            Consumer<Throwable> onError,
            Runnable onClose) throws Exception {
        return httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .buildAsync(uri, new EngineListener(onMessage, onError, onClose))
                .get(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    private static final class EngineListener implements WebSocket.Listener {

        private final Consumer<String> onMessage;
        private final Consumer<Throwable> onError;
        private final Runnable onClose;
        private final StringBuilder buffer = new StringBuilder();

        private EngineListener(
                Consumer<String> onMessage,
                Consumer<Throwable> onError,
                Runnable onClose) {
            this.onMessage = onMessage;
            this.onError = onError;
            this.onClose = onClose;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(
                WebSocket webSocket, CharSequence data, boolean last) {
            try {
                buffer.append(data);
                if (buffer.length() > MAX_ENGINE_RESPONSE_CHARS) {
                    throw new IllegalStateException("AI 姿态响应超过大小限制");
                }
                if (last) {
                    String payload = buffer.toString();
                    buffer.setLength(0);
                    onMessage.accept(payload);
                }
                webSocket.request(1);
            } catch (Exception exception) {
                webSocket.abort();
                onError.accept(exception);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(
                WebSocket webSocket, int statusCode, String reason) {
            onClose.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            onError.accept(error);
        }
    }
}
