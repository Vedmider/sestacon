package io.github.vedmider.sestacon.adapter.in.web;

import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.in.RagQueryUseCase;
import io.github.vedmider.sestacon.domain.port.in.RetrieveUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RetrieveController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = io.github.vedmider.sestacon.ratelimit.WebConfig.class))
class RetrieveControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RetrieveUseCase retrieve;

    // Present only to prove the retrieve path never touches the LLM use case.
    @MockBean
    private RagQueryUseCase ragQuery;

    @org.springframework.boot.test.context.TestConfiguration
    static class Props {
        @org.springframework.context.annotation.Bean
        io.github.vedmider.sestacon.config.RagProperties ragProperties() {
            return new io.github.vedmider.sestacon.config.RagProperties();
        }
    }

    @Test
    void returnsChunksAndNeverCallsTheLlm() throws Exception {
        when(retrieve.retrieve(eq("rollback policy"), eq(4), anyList(), anyMap()))
                .thenReturn(List.of(new RetrievedChunk("do X then Y", "guide.md", 0.91),
                        new RetrievedChunk("note Z", "adr.md", 0.80)));

        mockMvc.perform(post("/api/retrieve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"rollback policy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].text").value("do X then Y"))
                .andExpect(jsonPath("$[0].source").value("guide.md"))
                .andExpect(jsonPath("$[0].score").value(0.91));

        verifyNoInteractions(ragQuery);
    }

    @Test
    void honoursExplicitTopKAndRolesHeader() throws Exception {
        when(retrieve.retrieve(eq("q"), eq(7), eq(List.of("ops", "sec")), anyMap()))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/retrieve").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Roles", "ops, sec")
                        .content("{\"query\":\"q\",\"topK\":7}"))
                .andExpect(status().isOk());

        verify(retrieve).retrieve(eq("q"), eq(7), eq(List.of("ops", "sec")), anyMap());
    }

    @Test
    void topKIsCappedAt50() throws Exception {
        when(retrieve.retrieve(eq("q"), eq(50), anyList(), anyMap())).thenReturn(List.of());

        mockMvc.perform(post("/api/retrieve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"q\",\"topK\":9999}"))
                .andExpect(status().isOk());

        verify(retrieve).retrieve(eq("q"), eq(50), anyList(), anyMap());
    }

    @Test
    void blankQueryReturns400() throws Exception {
        mockMvc.perform(post("/api/retrieve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"   \"}"))
                .andExpect(status().isBadRequest());
        verify(retrieve, never()).retrieve(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), anyList(), anyMap());
    }

    @Test
    void bindsMetadataFilterFromBodyAndPassesItThrough() throws Exception {
        when(retrieve.retrieve(eq("q"), eq(4),
                anyList(), eq(java.util.Map.of("sourceSystem", "jira", "docType", "md"))))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/retrieve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"q\",\"metadata\":{\"sourceSystem\":\"jira\","
                                + "\"docType\":\"md\"}}"))
                .andExpect(status().isOk());

        verify(retrieve).retrieve(eq("q"), eq(4), anyList(),
                eq(java.util.Map.of("sourceSystem", "jira", "docType", "md")));
    }

    @Test
    void ignoresUnknownRequestFields() throws Exception {
        when(retrieve.retrieve(eq("q"), eq(4), anyList(), anyMap())).thenReturn(List.of());

        mockMvc.perform(post("/api/retrieve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"q\",\"somethingNew\":123,\"metadata\":{}}"))
                .andExpect(status().isOk());
    }

    @Test
    void overLongQueryReturns400() throws Exception {
        String longQuery = "a".repeat(5000);
        mockMvc.perform(post("/api/retrieve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + longQuery + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
