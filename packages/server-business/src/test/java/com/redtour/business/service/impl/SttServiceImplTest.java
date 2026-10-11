package com.redtour.business.service.impl;

import com.redtour.business.client.AiEngineClient;
import com.redtour.business.dto.SttResult;
import com.redtour.business.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SttServiceImplTest {

    @Test
    void shouldValidateAndNormalizeRecognitionResult() {
        AiEngineClient client = mock(AiEngineClient.class);
        SttServiceImpl service = new SttServiceImpl(client);
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "visitor.wav", "audio/wav", "RIFF-audio".getBytes());
        SttResult engineResult = new SttResult();
        engineResult.setText("  遵义会议具有重要历史意义。  ");
        engineResult.setDurationMs(-1L);
        when(client.transcribe(audio)).thenReturn(engineResult);

        SttResult result = service.transcribe(audio);

        assertEquals("遵义会议具有重要历史意义。", result.getText());
        assertEquals(0L, result.getDurationMs());
    }

    @Test
    void shouldRejectEmptyAndUnsupportedAudioBeforeCallingEngine() {
        AiEngineClient client = mock(AiEngineClient.class);
        SttServiceImpl service = new SttServiceImpl(client);
        MockMultipartFile empty = new MockMultipartFile(
                "audio", "empty.wav", "audio/wav", new byte[0]);
        MockMultipartFile unsupported = new MockMultipartFile(
                "audio", "payload.exe", "application/octet-stream", new byte[]{1});

        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.transcribe(empty)).getCode());
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.transcribe(unsupported)).getCode());
        verifyNoInteractions(client);
    }

    @Test
    void shouldRejectUnsupportedContentTypeBeforeCallingEngine() {
        AiEngineClient client = mock(AiEngineClient.class);
        SttServiceImpl service = new SttServiceImpl(client);
        MockMultipartFile image = new MockMultipartFile(
                "audio", "voice.wav", "image/png", new byte[]{1});

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.transcribe(image));

        assertEquals(400, exception.getCode());
        verifyNoInteractions(client);
    }

    @Test
    void shouldRejectOversizedAudioBeforeCallingEngine() {
        AiEngineClient client = mock(AiEngineClient.class);
        SttServiceImpl service = new SttServiceImpl(client);
        MultipartFile audio = mock(MultipartFile.class);
        when(audio.isEmpty()).thenReturn(false);
        when(audio.getSize()).thenReturn(20L * 1024L * 1024L + 1L);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.transcribe(audio));

        assertEquals(413, exception.getCode());
        verifyNoInteractions(client);
    }

    @Test
    void shouldRejectBlankRecognitionResult() {
        AiEngineClient client = mock(AiEngineClient.class);
        SttServiceImpl service = new SttServiceImpl(client);
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "silence.ogg", "audio/ogg", new byte[]{1});
        SttResult engineResult = new SttResult();
        engineResult.setText(" ");
        when(client.transcribe(audio)).thenReturn(engineResult);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.transcribe(audio));

        assertEquals(422, exception.getCode());
    }
}
