package com.redtour.business.controller;

import com.redtour.business.client.AiEngineClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 音频公开代理：前端访问业务后端，由业务后端读取 AI 引擎内部 TTS 文件。
 */
@RestController
@RequiredArgsConstructor
public class AudioController {

    private final AiEngineClient aiEngineClient;

    @GetMapping(value = "/audio/tts/{filename}", produces = "audio/wav")
    public ResponseEntity<byte[]> getTtsAudio(@PathVariable String filename) {
        byte[] audio = aiEngineClient.fetchTtsAudio(filename);
        if (audio == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(audio);
    }
}
