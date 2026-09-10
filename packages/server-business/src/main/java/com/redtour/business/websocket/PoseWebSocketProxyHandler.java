package com.redtour.business.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redtour.business.config.PoseWebSocketProperties;
import com.redtour.business.dto.PoseRecognizeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 触摸屏姿态流代理：绑定设备/景区、限制资源并转发到 AI 引擎。
 */
@Slf4j
@Component
public class PoseWebSocketProxyHandler extends TextWebSocketHandler {

    private static final Pattern DEVICE_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,49}");
    private static final CloseStatus INVALID_BINDING =
            new CloseStatus(1008, "deviceId 或 scenicAreaId 无效");
    private static final CloseStatus INVALID_FRAME =
            new CloseStatus(1007, "姿态帧消息无效");
    private static final CloseStatus MESSAGE_TOO_LARGE =
            new CloseStatus(1009, "姿态帧消息过大");
    private static final CloseStatus CONNECTION_LIMIT =
            new CloseStatus(1013, "姿态连接数已满");
    private static final CloseStatus ENGINE_UNAVAILABLE =
            new CloseStatus(1011, "姿态识别引擎不可用");

    private final PoseEngineSocketConnector connector;
    private final ObjectMapper objectMapper;
    private final PoseWebSocketProperties properties;
    private final Map<String, ProxySession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger activeConnections = new AtomicInteger();

    public PoseWebSocketProxyHandler(
            PoseEngineSocketConnector connector,
            ObjectMapper objectMapper,
            PoseWebSocketProperties properties) {
        this.connector = connector;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Binding binding = parseBinding(session.getUri());
        if (binding == null) {
            session.close(INVALID_BINDING);
            return;
        }
        session.setTextMessageSizeLimit(properties.getMaxMessageChars());
        session.setBinaryMessageSizeLimit(1024);
        if (activeConnections.incrementAndGet() > properties.getMaxConnections()) {
            activeConnections.decrementAndGet();
            session.close(CONNECTION_LIMIT);
            return;
        }

        ProxySession state = new ProxySession(
                session,
                binding.deviceId(),
                new PoseStabilityTracker(
                        properties.getStableFrames(), properties.getMinConfidence()));
        sessions.put(session.getId(), state);
        try {
            URI engineUri = UriComponentsBuilder
                    .fromUriString(properties.getAiEngineUrl())
                    .queryParam("scenicAreaId", binding.scenicAreaId())
                    .build()
                    .encode()
                    .toUri();
            WebSocket engineSocket = connector.connect(
                    engineUri,
                    payload -> handleEngineMessage(session.getId(), payload),
                    error -> handleEngineError(session.getId(), error),
                    () -> handleEngineClose(session.getId()));
            state.engineSocket = engineSocket;
            if (!sessions.containsKey(session.getId())) {
                engineSocket.abort();
            }
            log.info("[姿态WS] 代理连接建立 (deviceId={}, scenicAreaId={})",
                    binding.deviceId(), binding.scenicAreaId());
        } catch (Exception exception) {
            log.warn("[姿态WS] AI 引擎连接失败 (deviceId={})", binding.deviceId(), exception);
            closeAndCleanup(session.getId(), ENGINE_UNAVAILABLE, true);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ProxySession state = sessions.get(session.getId());
        if (state == null) {
            closeQuietly(session, ENGINE_UNAVAILABLE);
            return;
        }
        state.lastClientActivityMillis = System.currentTimeMillis();
        if (message.getPayloadLength() > properties.getMaxMessageChars()) {
            closeAndCleanup(session.getId(), MESSAGE_TOO_LARGE, true);
            return;
        }
        final String forwardPayload;
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            JsonNode frameNode = root == null ? null : root.get("frame");
            if (root == null || !root.isObject() || frameNode == null
                    || !frameNode.isTextual() || frameNode.textValue().isBlank()) {
                closeAndCleanup(session.getId(), INVALID_FRAME, true);
                return;
            }
            String frame = frameNode.textValue().trim();
            if (frame.length() > properties.getMaxMessageChars()) {
                closeAndCleanup(session.getId(), MESSAGE_TOO_LARGE, true);
                return;
            }
            ObjectNode forward = objectMapper.createObjectNode();
            forward.put("frame", frame);
            forwardPayload = objectMapper.writeValueAsString(forward);
        } catch (Exception exception) {
            closeAndCleanup(session.getId(), INVALID_FRAME, true);
            return;
        }

        WebSocket engineSocket = state.engineSocket;
        if (engineSocket == null) {
            closeAndCleanup(session.getId(), ENGINE_UNAVAILABLE, true);
            return;
        }
        try {
            synchronized (state) {
                state.sendTail = state.sendTail.thenCompose(ignored ->
                        engineSocket.sendText(forwardPayload, true).thenApply(sent -> null));
                state.sendTail.whenComplete((ignored, error) -> {
                    if (error != null) {
                        handleEngineError(session.getId(), error);
                    }
                });
            }
        } catch (Exception exception) {
            handleEngineError(session.getId(), exception);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        closeAndCleanup(session.getId(), INVALID_FRAME, true);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[姿态WS] 客户端传输异常 (sessionId={})", session.getId(), exception);
        closeAndCleanup(session.getId(), CloseStatus.SERVER_ERROR, true);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cleanup(session.getId(), true);
    }

    @Scheduled(fixedDelayString = "${pose.websocket.idle-scan-ms:10000}")
    public void closeIdleSessions() {
        closeIdleSessionsAt(System.currentTimeMillis());
    }

    void closeIdleSessionsAt(long nowMillis) {
        long timeout = Math.max(1L, properties.getIdleTimeoutMs());
        sessions.forEach((sessionId, state) -> {
            if (nowMillis - state.lastClientActivityMillis >= timeout) {
                closeAndCleanup(
                        sessionId,
                        new CloseStatus(1001, "姿态连接空闲超时"),
                        true);
            }
        });
    }

    private void handleEngineMessage(String sessionId, String payload) {
        ProxySession state = sessions.get(sessionId);
        if (state == null) {
            return;
        }
        try {
            PoseRecognizeResult result = objectMapper.readValue(payload, PoseRecognizeResult.class);
            PoseRecognizeResult gated = state.stabilityTracker.gate(result);
            String clientPayload = objectMapper.writeValueAsString(gated);
            synchronized (state.clientSession) {
                if (state.clientSession.isOpen()) {
                    state.clientSession.sendMessage(new TextMessage(clientPayload));
                }
            }
        } catch (Exception exception) {
            log.warn("[姿态WS] AI 响应无效 (deviceId={})", state.deviceId, exception);
            closeAndCleanup(sessionId, ENGINE_UNAVAILABLE, true);
        }
    }

    private void handleEngineError(String sessionId, Throwable error) {
        ProxySession state = sessions.get(sessionId);
        if (state != null) {
            log.warn("[姿态WS] AI 连接异常 (deviceId={})", state.deviceId, error);
        }
        closeAndCleanup(sessionId, ENGINE_UNAVAILABLE, true);
    }

    private void handleEngineClose(String sessionId) {
        ProxySession state = sessions.remove(sessionId);
        if (state == null) {
            return;
        }
        activeConnections.decrementAndGet();
        closeQuietly(state.clientSession, CloseStatus.GOING_AWAY);
    }

    private Binding parseBinding(URI uri) {
        if (uri == null) {
            return null;
        }
        try {
            var query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
            String deviceId = query.getFirst("deviceId");
            String scenicAreaValue = query.getFirst("scenicAreaId");
            if (deviceId == null || !DEVICE_ID_PATTERN.matcher(deviceId).matches()) {
                return null;
            }
            long scenicAreaId = Long.parseLong(scenicAreaValue == null ? "" : scenicAreaValue);
            return scenicAreaId > 0 ? new Binding(deviceId, scenicAreaId) : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void closeAndCleanup(String sessionId, CloseStatus status, boolean abortEngine) {
        ProxySession state = sessions.get(sessionId);
        if (state != null) {
            closeQuietly(state.clientSession, status);
        }
        cleanup(sessionId, abortEngine);
    }

    private void cleanup(String sessionId, boolean abortEngine) {
        ProxySession state = sessions.remove(sessionId);
        if (state == null) {
            return;
        }
        activeConnections.decrementAndGet();
        if (abortEngine && state.engineSocket != null) {
            state.engineSocket.abort();
        }
        log.info("[姿态WS] 代理连接释放 (deviceId={})", state.deviceId);
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (Exception exception) {
            log.debug("[姿态WS] 关闭客户端连接失败 (sessionId={})", session.getId(), exception);
        }
    }

    private record Binding(String deviceId, long scenicAreaId) {
    }

    private static final class ProxySession {

        private final WebSocketSession clientSession;
        private final String deviceId;
        private final PoseStabilityTracker stabilityTracker;
        private CompletableFuture<Void> sendTail = CompletableFuture.completedFuture(null);
        private volatile long lastClientActivityMillis = System.currentTimeMillis();
        private volatile WebSocket engineSocket;

        private ProxySession(
                WebSocketSession clientSession,
                String deviceId,
                PoseStabilityTracker stabilityTracker) {
            this.clientSession = clientSession;
            this.deviceId = deviceId;
            this.stabilityTracker = stabilityTracker;
        }
    }
}
