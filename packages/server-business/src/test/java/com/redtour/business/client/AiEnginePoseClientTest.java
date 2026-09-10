package com.redtour.business.client;

import com.redtour.business.dto.PoseRecognizeRequest;
import com.redtour.business.dto.PoseRecognizeResult;
import com.redtour.business.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiEnginePoseClientTest {

    @Test
    void shouldForwardPoseRequestToEngine() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AiEngineClient client = client(restTemplate);
        PoseRecognizeRequest request = request();
        PoseRecognizeResult expected = new PoseRecognizeResult();
        expected.setAction("wave");
        expected.setConfidence(0.9);
        when(restTemplate.exchange(
                eq("http://localhost:8001/engine/pose"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(PoseRecognizeResult.class)))
                .thenReturn(ResponseEntity.ok(expected));

        PoseRecognizeResult result = client.recognizePose(request);

        assertEquals(expected, result);
    }

    @Test
    void shouldReturnUnknownWhenEngineIsUnavailable() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AiEngineClient client = client(restTemplate);
        PoseRecognizeRequest request = request();
        when(restTemplate.exchange(
                eq("http://localhost:8001/engine/pose"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(PoseRecognizeResult.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        PoseRecognizeResult result = client.recognizePose(request);

        assertEquals("unknown", result.getAction());
        assertEquals(0.0, result.getConfidence());
        assertEquals(0, result.getKeypoints().size());
        assertNull(result.getTriggerContent());
        assertNotNull(result.getTimestamp());
    }

    @Test
    void shouldMapInvalidImageToBusinessParameterError() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AiEngineClient client = client(restTemplate);
        PoseRecognizeRequest request = request();
        when(restTemplate.exchange(
                eq("http://localhost:8001/engine/pose"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(PoseRecognizeResult.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> client.recognizePose(request));

        assertEquals(400, exception.getCode());
        assertEquals("Base64 图片格式错误或超过限制", exception.getMessage());
    }

    private AiEngineClient client(RestTemplate restTemplate) {
        AiEngineClient client = new AiEngineClient(restTemplate);
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:8001");
        return client;
    }

    private PoseRecognizeRequest request() {
        PoseRecognizeRequest request = new PoseRecognizeRequest();
        request.setFrame("aW1hZ2U=");
        request.setScenicAreaId(1L);
        return request;
    }
}
