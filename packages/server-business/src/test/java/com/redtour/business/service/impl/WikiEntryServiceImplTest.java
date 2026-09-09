package com.redtour.business.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redtour.business.client.AiEngineClient;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.WikiEntryResult;
import com.redtour.business.dto.WikiUpdateRequest;
import com.redtour.business.dto.WikiUploadRequest;
import com.redtour.business.entity.SysUser;
import com.redtour.business.entity.WikiEntry;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.WikiEntryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikiEntryServiceImplTest {

    private WikiEntryMapper wikiEntryMapper;
    private AiEngineClient aiEngineClient;
    private WikiEntryServiceImpl wikiEntryService;

    @BeforeEach
    void setUp() {
        wikiEntryMapper = mock(WikiEntryMapper.class);
        aiEngineClient = mock(AiEngineClient.class);
        wikiEntryService = new WikiEntryServiceImpl(wikiEntryMapper, aiEngineClient, new ObjectMapper());
        ReflectionTestUtils.setField(wikiEntryService, "wikiBuildPath", "target/wiki-build-test");
        SysUser admin = new SysUser();
        admin.setId(2L);
        admin.setRole("admin");
        admin.setScenicAreaId(1L);
        UserContext.set(admin);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldListOnlyAuthorizedScenicAreaAndConvertJsonFields() {
        WikiEntry entry = entry(7L, 1L, "遵义会议", "done");
        when(wikiEntryMapper.findPage(1L, "会议", 0, 10)).thenReturn(List.of(entry));
        when(wikiEntryMapper.count(1L, "会议")).thenReturn(1L);

        var page = wikiEntryService.list(1L, 1, 10, " 会议 ");

        assertEquals(1L, page.getTotal());
        assertEquals("遵义会议", page.getList().get(0).getTitle());
        assertEquals(List.of("长征"), page.getList().get(0).getTags());
        assertEquals(List.of("wiki/长征"), page.getList().get(0).getLinks());
        verify(wikiEntryMapper).findPage(1L, "会议", 0, 10);
    }

    @Test
    void shouldRejectCrossScenicAreaAccessForAdmin() {
        assertThrows(BusinessException.class, () -> wikiEntryService.list(2L, 1, 10, null));
        verify(wikiEntryMapper, never()).findPage(any(), any(), eq(0), eq(10));
    }

    @Test
    void shouldUploadEntryAndPersistSuccessfulCompileResult() {
        WikiUploadRequest request = new WikiUploadRequest();
        request.setScenicAreaId(1L);
        request.setTitle("遵义/会议");
        request.setContent("1935年召开。");
        request.setTags(List.of(" 长征 ", "长征"));
        when(wikiEntryMapper.insert(any(WikiEntry.class))).thenAnswer(invocation -> {
            WikiEntry inserted = invocation.getArgument(0);
            inserted.setId(12L);
            return 1;
        });
        when(wikiEntryMapper.findById(12L)).thenReturn(entry(12L, 1L, "遵义/会议", "pending"));
        when(wikiEntryMapper.markCompiling(12L)).thenReturn(1);
        when(aiEngineClient.compileWiki(any())).thenReturn(Map.of(
                "status", "done",
                "file_path", "wiki/scenic_1/遵义_会议.md",
                "links", List.of("wiki/长征")
        ));

        WikiEntryResult result = wikiEntryService.upload(request);

        assertEquals(12L, result.getId());
        assertEquals("done", result.getCompileStatus());
        assertEquals("wiki/scenic_1/遵义_会议.md", result.getFilePath());
        assertEquals(List.of("wiki/长征"), result.getLinks());
        verify(wikiEntryMapper).markDone(12L, "wiki/scenic_1/遵义_会议.md", "[\"wiki/长征\"]");
    }

    @Test
    void shouldResetCompileStatusToPendingWhenEdited() {
        WikiEntry existing = entry(4L, 1L, "旧标题", "done");
        when(wikiEntryMapper.findById(4L)).thenReturn(existing);
        when(wikiEntryMapper.updateContent(any(WikiEntry.class))).thenReturn(1);
        WikiUpdateRequest request = new WikiUpdateRequest();
        request.setTitle("新标题");
        request.setContent("新正文");
        request.setTags(List.of("历史"));

        WikiEntryResult result = wikiEntryService.update(4L, request);

        assertEquals("新标题", result.getTitle());
        assertEquals("pending", result.getCompileStatus());
        verify(wikiEntryMapper).updateContent(existing);
    }

    @Test
    void shouldPersistFailedStatusWhenAiEngineUnavailable() {
        WikiEntry existing = entry(5L, 1L, "条目", "pending");
        when(wikiEntryMapper.findById(5L)).thenReturn(existing);
        when(wikiEntryMapper.markCompiling(5L)).thenReturn(1);
        when(aiEngineClient.compileWiki(any())).thenReturn(Map.of("status", "failed", "error", "unavailable"));

        WikiEntryResult result = wikiEntryService.compile(5L);

        assertEquals("failed", result.getCompileStatus());
        verify(wikiEntryMapper).markFailed(5L);
    }

    @Test
    void shouldRejectAiCompiledPathTraversal() {
        WikiEntry existing = entry(6L, 1L, "条目", "pending");
        when(wikiEntryMapper.findById(6L)).thenReturn(existing);
        when(wikiEntryMapper.markCompiling(6L)).thenReturn(1);
        when(aiEngineClient.compileWiki(any())).thenReturn(Map.of(
                "status", "done",
                "file_path", "../../outside.md",
                "links", List.of()
        ));

        WikiEntryResult result = wikiEntryService.compile(6L);

        assertEquals(existing.getFilePath(), result.getFilePath());
        verify(wikiEntryMapper).markDone(eq(6L), eq(existing.getFilePath()), eq("[]"));
    }

    @Test
    void shouldRejectInvalidPageBeforeQueryingDatabase() {
        assertThrows(BusinessException.class, () -> wikiEntryService.list(1L, 0, 10, null));
        verify(wikiEntryMapper, never()).findPage(any(), any(), any(Integer.class), any(Integer.class));
    }

    private WikiEntry entry(Long id, Long scenicAreaId, String title, String status) {
        WikiEntry entry = new WikiEntry();
        entry.setId(id);
        entry.setScenicAreaId(scenicAreaId);
        entry.setTitle(title);
        entry.setFilePath("wiki/scenic_" + scenicAreaId + "/" + title + ".md");
        entry.setContent("正文");
        entry.setTags("[\"长征\"]");
        entry.setLinks("[\"wiki/长征\"]");
        entry.setCompileStatus(status);
        return entry;
    }
}
