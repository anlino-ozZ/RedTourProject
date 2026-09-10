package com.redtour.business.client;

import com.redtour.business.dto.SttResult;
import com.redtour.business.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiEngineSttClientTest {

    @Test
    void shouldSendAudioAsMultipartField() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AiEngineClient client = new AiEngineClient(restTemplate);
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:8001");
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "..\\visitor.wav", "audio/wav", "RIFF-audio".getBytes());
        SttResult expected = new SttResult();
        expected.setText("遵义会议");
        expected.setDurationMs(100L);
        when(restTemplate.exchange(
                eq("http://localhost:8001/engine/stt"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(SttResult.class)))
                .thenReturn(ResponseEntity.ok(expected));

        SttResult result = client.transcribe(audio);

        assertEquals(expected, result);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://localhost:8001/engine/stt"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(SttResult.class));
        assertInstanceOf(MultiValueMap.class, captor.getValue().getBody());
        @SuppressWarnings("unchecked")
        MultiValueMap<String, Object> body =
                (MultiValueMap<String, Object>) captor.getValue().getBody();
        HttpEntity<?> filePart = assertInstanceOf(HttpEntity.class, body.getFirst("audio"));
        ByteArrayResource resource =
                assertInstanceOf(ByteArrayResource.class, filePart.getBody());
        assertEquals("visitor.wav", resource.getFilename());
        assertEquals("RIFF-audio", new String(resource.getByteArray()));
    }

    @Test
    void shouldMapEngineValidationErrorWithoutLeakingResponse() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AiEngineClient client = new AiEngineClient(restTemplate);
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:8001");
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "silence.wav", "audio/wav", new byte[]{1});
        when(restTemplate.exchange(
                eq("http://localhost:8001/engine/stt"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(SttResult.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Unprocessable Entity",
                        null,
                        null,
                        null));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> client.transcribe(audio));

        assertEquals(422, exception.getCode());
        assertEquals("音频中未识别到有效语音，或时长超过限制", exception.getMessage());
    }

    @Test
    void shouldMapUnavailableLocalModelToServiceUnavailable() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AiEngineClient client = new AiEngineClient(restTemplate);
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:8001");
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "visitor.wav", "audio/wav", new byte[]{1});
        when(restTemplate.exchange(
                eq("http://localhost:8001/engine/stt"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(SttResult.class)))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Service Unavailable",
                        null,
                        null,
                        null));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> client.transcribe(audio));

        assertEquals(503, exception.getCode());
        assertEquals("语音识别服务暂不可用", exception.getMessage());
    }
}
