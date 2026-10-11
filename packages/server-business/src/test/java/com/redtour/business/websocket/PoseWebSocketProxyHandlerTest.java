package com.redtour.business.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redtour.business.config.PoseWebSocketProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PoseWebSocketProxyHandlerTest {

    private ObjectMapper objectMapper;
    private PoseWebSocketProperties properties;
    private FakeConnector connector;
    private PoseWebSocketProxyHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new PoseWebSocketProperties();
        connector = new FakeConnector();
        handler = new PoseWebSocketProxyHandler(connector, objectMapper, properties);
    }

    @Test
    void shouldBindContextForwardFramesAndGateTriggerContent() throws Exception {
        WebSocketSession session = session(
                "one", "/api/v1/pose/stream?deviceId=screen-01&scenicAreaId=7");
        handler.afterConnectionEstablished(session);

        assertEquals("7", queryValue(connector.uri, "scenicAreaId"));
        for (int index = 0; index < 3; index++) {
            handler.handleTextMessage(session, new TextMessage(
                    "{\"frame\":\"aW1hZ2U=\",\"scenicAreaId\":999}"));
            connector.onMessage.accept(engineResult("salute", 0.92));
        }

        ArgumentCaptor<String> forwarded = ArgumentCaptor.forClass(String.class);
        verify(connector.engineSocket, atLeastOnce()).sendText(forwarded.capture(), eq(true));
        JsonNode engineRequest = objectMapper.readTree(forwarded.getAllValues().get(0));
        assertEquals("aW1hZ2U=", engineRequest.get("frame").asText());
        assertTrue(engineRequest.get("scenicAreaId") == null);

        ArgumentCaptor<TextMessage> responses = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, org.mockito.Mockito.times(3)).sendMessage(responses.capture());
        List<TextMessage> messages = responses.getAllValues();
        assertTrue(objectMapper.readTree(messages.get(0).getPayload())
                .get("triggerContent").isNull());
        assertTrue(objectMapper.readTree(messages.get(1).getPayload())
                .get("triggerContent").isNull());
        assertNotNull(objectMapper.readTree(messages.get(2).getPayload())
                .get("triggerContent").get("title"));
    }

    @Test
    void shouldRejectMissingBindingBeforeConnectingEngine() throws Exception {
        WebSocketSession session = session(
                "invalid", "/api/v1/pose/stream?deviceId=screen-01");

        handler.afterConnectionEstablished(session);

        verify(session).close(new CloseStatus(1008, "deviceId 或 scenicAreaId 无效"));
        assertEquals(0, connector.connectCalls);
    }

    @Test
    void shouldEnforceMessageAndConnectionLimits() throws Exception {
        properties.setMaxConnections(1);
        properties.setMaxMessageChars(20);
        WebSocketSession first = session(
                "first", "/api/v1/pose/stream?deviceId=screen-01&scenicAreaId=1");
        WebSocketSession second = session(
                "second", "/api/v1/pose/stream?deviceId=screen-02&scenicAreaId=1");
        handler.afterConnectionEstablished(first);
        verify(first).setTextMessageSizeLimit(20);
        verify(first).setBinaryMessageSizeLimit(1024);

        handler.afterConnectionEstablished(second);
        verify(second).close(new CloseStatus(1013, "姿态连接数已满"));

        handler.handleTextMessage(first, new TextMessage(
                "{\"frame\":\"12345678901234567890\"}"));
        verify(first).close(new CloseStatus(1009, "姿态帧消息过大"));
        verify(connector.engineSocket).abort();
    }

    @Test
    void shouldCloseIdleClientAndReleaseEngineConnection() throws Exception {
        properties.setIdleTimeoutMs(100);
        WebSocketSession session = session(
                "idle", "/api/v1/pose/stream?deviceId=screen-01&scenicAreaId=1");
        handler.afterConnectionEstablished(session);

        handler.closeIdleSessionsAt(System.currentTimeMillis() + 1_000);

        verify(session).close(new CloseStatus(1001, "姿态连接空闲超时"));
        verify(connector.engineSocket).abort();
    }

    @Test
    void shouldReleaseEngineConnectionWhenClientDisconnects() throws Exception {
        WebSocketSession session = session(
                "cleanup", "/api/v1/pose/stream?deviceId=screen-01&scenicAreaId=1");
        handler.afterConnectionEstablished(session);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(connector.engineSocket).abort();
    }

    private WebSocketSession session(String id, String uri) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getUri()).thenReturn(URI.create(uri));
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private String engineResult(String action, double confidence) throws Exception {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("action", action);
        result.put("confidence", confidence);
        result.set("keypoints", objectMapper.createArrayNode());
        result.set("triggerContent", objectMapper.createObjectNode()
                .put("title", "军礼的由来")
                .put("audioUrl", "/audio/pose/salute.mp3")
                .put("wikiRef", "wiki/军礼"));
        result.put("timestamp", 123);
        return objectMapper.writeValueAsString(result);
    }

    private String queryValue(URI uri, String name) {
        String query = uri.getQuery();
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(name)) {
                return pair[1];
            }
        }
        return null;
    }

    private static final class FakeConnector implements PoseEngineSocketConnector {

        private final WebSocket engineSocket = mock(WebSocket.class);
        private URI uri;
        private Consumer<String> onMessage;
        private int connectCalls;

        private FakeConnector() {
            when(engineSocket.sendText(anyString(), eq(true)))
                    .thenReturn(CompletableFuture.completedFuture(engineSocket));
        }

        @Override
        public WebSocket connect(
                URI uri,
                Consumer<String> onMessage,
                Consumer<Throwable> onError,
                Runnable onClose) {
            this.uri = uri;
            this.onMessage = onMessage;
            this.connectCalls++;
            return engineSocket;
        }
    }
}
