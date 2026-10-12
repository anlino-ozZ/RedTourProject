package com.redtour.business.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redtour.business.client.AiEngineClient;
import com.redtour.business.dto.AskRequest;
import com.redtour.business.dto.AskResult;
import com.redtour.business.entity.AskLog;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.AskLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AskServiceImplTest {

    private AiEngineClient aiEngineClient;
    private AskLogMapper askLogMapper;
    private AskServiceImpl askService;

    @BeforeEach
    void setUp() {
        aiEngineClient = mock(AiEngineClient.class);
        askLogMapper = mock(AskLogMapper.class);
        askService = new AskServiceImpl(aiEngineClient, askLogMapper, new ObjectMapper());
    }

    @Test
    void shouldReturnAiResultAndPersistCompleteAskLog() {
        AskRequest request = request(1L);
        AskResult result = result();
        when(aiEngineClient.ask(request)).thenReturn(result);

        AskResult actual = askService.ask(request, "  rpi-001  ");

        assertSame(result, actual);
        ArgumentCaptor<AskLog> captor = ArgumentCaptor.forClass(AskLog.class);
        verify(askLogMapper).insert(captor.capture());
        AskLog log = captor.getValue();
        assertEquals(1L, log.getScenicAreaId());
        assertEquals(request.getQuestion(), log.getQuestion());
        assertEquals(result.getAnswer(), log.getAnswer());
        assertEquals("[\"wiki/遵义会议\"]", log.getSources());
        assertEquals(2100L, log.getDurationMs());
        assertEquals("rpi-001", log.getDeviceId());
    }

    @Test
    void shouldNotFailAnswerWhenLogInsertFails() {
        AskRequest request = request(1L);
        AskResult result = result();
        when(aiEngineClient.ask(request)).thenReturn(result);
        when(askLogMapper.insert(any(AskLog.class))).thenThrow(new RuntimeException("db unavailable"));

        AskResult actual = askService.ask(request, null);

        assertSame(result, actual);
    }

    @Test
    void shouldSkipLogWhenOptionalScenicAreaIsMissing() {
        AskRequest request = request(null);
        AskResult result = result();
        when(aiEngineClient.ask(request)).thenReturn(result);

        assertSame(result, askService.ask(request, null));
        verify(askLogMapper, never()).insert(any(AskLog.class));
    }

    @Test
    void shouldRejectOversizedDeviceIdBeforeCallingAi() {
        AskRequest request = request(1L);

        assertThrows(BusinessException.class, () -> askService.ask(request, "x".repeat(51)));
        verify(aiEngineClient, never()).ask(any());
    }

    private AskRequest request(Long scenicAreaId) {
        AskRequest request = new AskRequest();
        request.setQuestion("遵义会议的意义是什么？");
        request.setScenicAreaId(scenicAreaId);
        request.setUseVoice(false);
        return request;
    }

    private AskResult result() {
        AskResult result = new AskResult();
        result.setQuestion("遵义会议的意义是什么？");
        result.setAnswer("遵义会议是中国革命的重要转折点。");
        result.setSources(List.of("wiki/遵义会议"));
        result.setDurationMs(2100L);
        return result;
    }
}
