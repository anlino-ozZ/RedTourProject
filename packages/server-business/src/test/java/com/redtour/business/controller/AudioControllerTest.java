package com.redtour.business.controller;

import com.redtour.business.client.AiEngineClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AudioControllerTest {

    private AiEngineClient aiEngineClient;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        aiEngineClient = mock(AiEngineClient.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AudioController(aiEngineClient)).build();
    }

    @Test
    void shouldProxyTtsAudioAsWav() throws Exception {
        byte[] audio = "RIFF".getBytes();
        when(aiEngineClient.fetchTtsAudio("ask_0123456789abcdef0123456789abcdef.wav"))
                .thenReturn(audio);

        mockMvc.perform(get("/audio/tts/ask_0123456789abcdef0123456789abcdef.wav"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/wav"))
                .andExpect(content().bytes(audio));

        verify(aiEngineClient).fetchTtsAudio("ask_0123456789abcdef0123456789abcdef.wav");
    }

    @Test
    void shouldReturnNotFoundWhenTtsAudioIsUnavailable() throws Exception {
        when(aiEngineClient.fetchTtsAudio("ask_0123456789abcdef0123456789abcdef.wav"))
                .thenReturn(null);

        mockMvc.perform(get("/audio/tts/ask_0123456789abcdef0123456789abcdef.wav"))
                .andExpect(status().isNotFound());
    }
}
