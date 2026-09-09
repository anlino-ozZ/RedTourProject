package com.redtour.business.controller;

import com.redtour.business.common.PageResult;
import com.redtour.business.dto.WikiEntryResult;
import com.redtour.business.exception.GlobalExceptionHandler;
import com.redtour.business.service.WikiEntryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WikiEntryControllerTest {

    private WikiEntryService wikiEntryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        wikiEntryService = mock(WikiEntryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WikiEntryController(wikiEntryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldExposePagedListContract() throws Exception {
        WikiEntryResult item = new WikiEntryResult(1L, 1L, "遵义会议", "wiki/遵义会议.md",
                List.of("长征"), List.of(), "done", null, null);
        when(wikiEntryService.list(1L, 1, 10, null))
                .thenReturn(PageResult.of(List.of(item), 1, 1, 10));

        mockMvc.perform(get("/api/v1/admin/wiki").param("scenicAreaId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].id").value(1))
                .andExpect(jsonPath("$.data.list[0].compileStatus").value("done"))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(wikiEntryService).list(1L, 1, 10, null);
    }

    @Test
    void shouldRejectMissingScenicAreaId() throws Exception {
        mockMvc.perform(get("/api/v1/admin/wiki"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("缺少必传参数: scenicAreaId"));
        verifyNoInteractions(wikiEntryService);
    }

    @Test
    void shouldValidateUploadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/wiki/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenicAreaId\":1,\"title\":\"\",\"content\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(wikiEntryService);
    }

    @Test
    void shouldRejectInvalidPageParameter() throws Exception {
        mockMvc.perform(get("/api/v1/admin/wiki")
                        .param("scenicAreaId", "1")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(wikiEntryService);
    }

    @Test
    void shouldExposeDetailCompileAndDeleteEndpoints() throws Exception {
        WikiEntryResult result = new WikiEntryResult(1L, 1L, "遵义会议", "wiki/遵义会议.md",
                List.of(), List.of(), "done", null, "正文");
        when(wikiEntryService.get(1L)).thenReturn(result);
        when(wikiEntryService.compile(1L)).thenReturn(result);

        mockMvc.perform(get("/api/v1/admin/wiki/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("正文"));
        mockMvc.perform(post("/api/v1/admin/wiki/1/compile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.compileStatus").value("done"));
        mockMvc.perform(delete("/api/v1/admin/wiki/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(wikiEntryService).get(1L);
        verify(wikiEntryService).compile(1L);
        verify(wikiEntryService).delete(1L);
    }

    @Test
    void shouldExposeUpdateEndpoint() throws Exception {
        WikiEntryResult result = new WikiEntryResult(1L, 1L, "新标题", "wiki/新标题.md",
                List.of("历史"), List.of(), "pending", null, "新正文");
        when(wikiEntryService.update(eq(1L), any())).thenReturn(result);

        mockMvc.perform(put("/api/v1/admin/wiki/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新标题\",\"content\":\"新正文\",\"tags\":[\"历史\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.compileStatus").value("pending"));

        verify(wikiEntryService).update(eq(1L), any());
    }
}
