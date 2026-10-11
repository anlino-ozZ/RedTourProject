package com.redtour.business.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redtour.business.client.AiEngineClient;
import com.redtour.business.common.PageResult;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.WikiCompileRequest;
import com.redtour.business.dto.WikiEntryResult;
import com.redtour.business.dto.WikiUpdateRequest;
import com.redtour.business.dto.WikiUploadRequest;
import com.redtour.business.entity.SysUser;
import com.redtour.business.entity.WikiEntry;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.WikiEntryMapper;
import com.redtour.business.service.WikiEntryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Wiki 条目管理服务：负责权限隔离、数据库状态流转以及 AI 编译结果落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiEntryServiceImpl implements WikiEntryService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_COMPILING = "compiling";
    private static final String STATUS_DONE = "done";
    private static final String STATUS_FAILED = "failed";
    private static final int MAX_PAGE_SIZE = 100;
    private static final Pattern UNSAFE_FILE_NAME = Pattern.compile("[\\p{Cntrl}<>:\"/\\\\|?*]+");

    private final WikiEntryMapper wikiEntryMapper;
    private final AiEngineClient aiEngineClient;
    private final ObjectMapper objectMapper;

    @Value("${data.wiki-build-path:./wiki_build}")
    private String wikiBuildPath;

    @Override
    public PageResult<WikiEntryResult> list(Long scenicAreaId, int page, int pageSize, String keyword) {
        validateScenicAreaId(scenicAreaId);
        validatePage(page, pageSize);
        authorizeScenicArea(scenicAreaId);

        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        long offsetValue = (long) (page - 1) * pageSize;
        if (offsetValue > Integer.MAX_VALUE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页范围过大");
        }
        int offset = (int) offsetValue;
        List<WikiEntry> pageEntries = wikiEntryMapper.findPage(scenicAreaId, normalizedKeyword, offset, pageSize);
        if (pageEntries == null) {
            pageEntries = List.of();
        }
        List<WikiEntryResult> entries = pageEntries
                .stream()
                .map(entry -> toResult(entry, false))
                .toList();
        return PageResult.of(entries, wikiEntryMapper.count(scenicAreaId, normalizedKeyword), page, pageSize);
    }

    @Override
    public WikiEntryResult upload(WikiUploadRequest request) {
        Objects.requireNonNull(request, "request");
        validateScenicAreaId(request.getScenicAreaId());
        authorizeScenicArea(request.getScenicAreaId());

        WikiEntry entry = new WikiEntry();
        entry.setScenicAreaId(request.getScenicAreaId());
        entry.setTitle(request.getTitle().trim());
        entry.setFilePath(buildFilePath(request.getScenicAreaId(), entry.getTitle()));
        entry.setContent(request.getContent());
        entry.setTags(toJsonArray(request.getTags()));
        entry.setLinks("[]");
        entry.setCompileStatus(STATUS_PENDING);
        wikiEntryMapper.insert(entry);
        if (entry.getId() == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Wiki条目创建失败");
        }
        return compile(entry.getId());
    }

    @Override
    public WikiEntryResult get(Long id) {
        WikiEntry entry = loadEntry(id);
        authorizeScenicArea(entry.getScenicAreaId());
        return toResult(entry, true);
    }

    @Override
    public WikiEntryResult update(Long id, WikiUpdateRequest request) {
        Objects.requireNonNull(request, "request");
        WikiEntry entry = loadEntry(id);
        authorizeScenicArea(entry.getScenicAreaId());

        entry.setTitle(request.getTitle().trim());
        entry.setFilePath(buildFilePath(entry.getScenicAreaId(), entry.getTitle()));
        entry.setContent(request.getContent());
        entry.setTags(toJsonArray(request.getTags()));
        entry.setLinks("[]");
        entry.setCompileStatus(STATUS_PENDING);
        wikiEntryMapper.updateContent(entry);
        return toResult(entry, true);
    }

    @Override
    public WikiEntryResult compile(Long id) {
        WikiEntry entry = loadEntry(id);
        authorizeScenicArea(entry.getScenicAreaId());
        if (wikiEntryMapper.markCompiling(id) <= 0) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Wiki编译状态更新失败");
        }
        entry.setCompileStatus(STATUS_COMPILING);

        Map<String, Object> response;
        try {
            response = aiEngineClient.compileWiki(new WikiCompileRequest(
                    entry.getScenicAreaId(), entry.getTitle(), entry.getContent(), parseArray(entry.getTags())));
        } catch (Exception exception) {
            log.error("[Wiki] 编译调用异常 (id={}, scenicAreaId={})", id, entry.getScenicAreaId(), exception);
            response = Map.of("status", STATUS_FAILED, "error", "AI引擎调用异常");
        }
        if (response == null) {
            response = Map.of("status", STATUS_FAILED, "error", "AI引擎返回空响应");
        }

        if (STATUS_DONE.equalsIgnoreCase(stringValue(response, "status"))) {
            String filePath = firstText(response, "file_path", "filePath");
            if (!StringUtils.hasText(filePath)) {
                filePath = entry.getFilePath();
            }
            filePath = normalizeCompiledPath(filePath, entry.getFilePath());
            List<String> links = parseAnyArray(response.get("links"));
            String linksJson = toJsonArray(links);
            wikiEntryMapper.markDone(id, filePath, linksJson);
            entry.setFilePath(filePath);
            entry.setLinks(linksJson);
            entry.setCompileStatus(STATUS_DONE);
        } else {
            String error = firstText(response, "error", "message");
            log.warn("[Wiki] 编译失败 (id={}, error={})", id, StringUtils.hasText(error) ? error : "unknown");
            wikiEntryMapper.markFailed(id);
            entry.setCompileStatus(STATUS_FAILED);
        }
        return toResult(entry, true);
    }

    @Override
    public void delete(Long id) {
        WikiEntry entry = loadEntry(id);
        authorizeScenicArea(entry.getScenicAreaId());
        wikiEntryMapper.deleteById(id);
        deleteCompiledArtifact(entry.getFilePath());
    }

    private WikiEntry loadEntry(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Wiki条目ID必须为正整数");
        }
        WikiEntry entry = wikiEntryMapper.findById(id);
        if (entry == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Wiki条目不存在");
        }
        return entry;
    }

    private void authorizeScenicArea(Long scenicAreaId) {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if ("super_admin".equals(current.getRole())) {
            return;
        }
        if (!"admin".equals(current.getRole())
                || current.getScenicAreaId() == null
                || !current.getScenicAreaId().equals(scenicAreaId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他景区的Wiki条目");
        }
    }

    private void validateScenicAreaId(Long scenicAreaId) {
        if (scenicAreaId == null || scenicAreaId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "景区ID必须为正整数");
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页参数不合法");
        }
    }

    private WikiEntryResult toResult(WikiEntry entry, boolean includeContent) {
        return new WikiEntryResult(entry.getId(), entry.getScenicAreaId(), entry.getTitle(), entry.getFilePath(),
                parseArray(entry.getTags()), parseArray(entry.getLinks()), entry.getCompileStatus(),
                entry.getUpdatedAt(), includeContent ? entry.getContent() : null);
    }

    private String buildFilePath(Long scenicAreaId, String title) {
        String safeTitle = UNSAFE_FILE_NAME.matcher(title.trim()).replaceAll("_");
        safeTitle = safeTitle.replaceAll("\\.{2,}", "_");
        if (!StringUtils.hasText(safeTitle)) {
            safeTitle = "wiki-entry";
        }
        if (safeTitle.length() > 80) {
            safeTitle = safeTitle.substring(0, 80);
        }
        return "wiki/scenic_" + scenicAreaId + "/" + safeTitle + ".md";
    }

    private void deleteCompiledArtifact(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        Path root = Path.of(wikiBuildPath).toAbsolutePath().normalize();
        Path target = root.resolve(filePath).normalize();
        if (!target.startsWith(root)) {
            log.warn("[Wiki] 拒绝删除越界编译产物 (filePath={})", filePath);
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            log.error("[Wiki] 删除编译产物失败 (filePath={})", filePath, exception);
        }
    }

    private String normalizeCompiledPath(String filePath, String fallback) {
        String normalizedValue = filePath.replace('\\', '/').trim();
        Path root = Path.of(wikiBuildPath).toAbsolutePath().normalize();
        Path target = root.resolve(normalizedValue).normalize();
        if (normalizedValue.isBlank() || normalizedValue.equals(".")
                || !normalizedValue.toLowerCase().endsWith(".md")
                || normalizedValue.startsWith("/") || !target.startsWith(root)) {
            log.warn("[Wiki] AI 返回越界编译路径，回退到受控路径 (filePath={})", filePath);
            return fallback;
        }
        return normalizedValue;
    }

    private String toJsonArray(List<String> values) {
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value) && seen.add(value.trim())) {
                    normalized.add(value.trim());
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException exception) {
            log.warn("[Wiki] JSON数组序列化失败，按空数组保存", exception);
            return "[]";
        }
    }

    private List<String> parseArray(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<>() {
            });
            return values == null ? List.of() : values.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (JsonProcessingException exception) {
            log.warn("[Wiki] JSON数组解析失败，按空数组返回", exception);
            return List.of();
        }
    }

    private List<String> parseAnyArray(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return value instanceof String text ? parseArray(text) : List.of();
    }

    private String stringValue(Map<String, Object> response, String key) {
        if (response == null) {
            return null;
        }
        Object value = response.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(Map<String, Object> response, String... keys) {
        if (response == null) {
            return null;
        }
        for (String key : keys) {
            String value = stringValue(response, key);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
