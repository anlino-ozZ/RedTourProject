package com.redtour.business.service.impl;

import com.redtour.business.client.AiEngineClient;
import com.redtour.business.common.ResultCode;
import com.redtour.business.dto.SttResult;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.service.SttService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

/**
 * STT 代理服务：在业务边界校验文件，再调用内部 AI 引擎。
 */
@Service
@RequiredArgsConstructor
public class SttServiceImpl implements SttService {

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of("wav", "mp3", "m4a", "webm", "ogg", "flac");
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "audio/wav",
            "audio/x-wav",
            "audio/wave",
            "audio/mpeg",
            "audio/mp3",
            "audio/mp4",
            "audio/x-m4a",
            "audio/webm",
            "audio/ogg",
            "audio/flac",
            "audio/x-flac",
            "application/ogg",
            "application/octet-stream"
    );

    private final AiEngineClient aiEngineClient;

    @Value("${ai-engine.stt-max-file-mb:20}")
    private long maxFileMb;

    @Override
    public SttResult transcribe(MultipartFile audio) {
        validate(audio);
        SttResult result = aiEngineClient.transcribe(audio);
        if (result == null || result.getText() == null || result.getText().isBlank()) {
            throw new BusinessException(422, "音频中未识别到有效语音");
        }
        result.setText(result.getText().trim());
        if (result.getDurationMs() == null || result.getDurationMs() < 0) {
            result.setDurationMs(0L);
        }
        return result;
    }

    private void validate(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "音频文件不能为空");
        }
        long effectiveMaxMb = maxFileMb > 0 ? maxFileMb : 20;
        if (audio.getSize() > effectiveMaxMb * 1024L * 1024L) {
            throw new BusinessException(413, "音频文件不能超过 " + effectiveMaxMb + "MB");
        }
        String filename = audio.getOriginalFilename();
        int extensionAt = filename == null ? -1 : filename.lastIndexOf('.');
        String extension = extensionAt < 0 ? ""
                : filename.substring(extensionAt + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的音频格式");
        }
        String contentType = audio.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!SUPPORTED_CONTENT_TYPES.contains(normalized)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的音频 Content-Type");
            }
        }
    }
}
