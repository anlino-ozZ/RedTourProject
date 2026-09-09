package com.redtour.business.client;

import com.redtour.business.dto.AskRequest;
import com.redtour.business.dto.AskResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiEngineClientTest {

    @Test
    void shouldReturnCompleteFallbackAndEchoQuestionWhenEngineUnavailable() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AiEngineClient client = new AiEngineClient(restTemplate);
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:8001");
        when(restTemplate.exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(AskResult.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        AskRequest request = new AskRequest();
        request.setQuestion("长征路上有哪些关键决策？");

        AskResult result = client.ask(request);

        assertEquals(request.getQuestion(), result.getQuestion());
        assertNotNull(result.getAnswer());
        assertEquals(0, result.getSources().size());
        assertEquals(0L, result.getDurationMs());
        assertNull(result.getAudioUrl());
    }
}
