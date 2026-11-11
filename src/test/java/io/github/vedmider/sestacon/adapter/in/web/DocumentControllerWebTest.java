package io.github.vedmider.sestacon.adapter.in.web;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.port.in.DeleteDocumentUseCase;
import io.github.vedmider.sestacon.domain.port.in.IngestChunksUseCase;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DocumentController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = io.github.vedmider.sestacon.ratelimit.WebConfig.class))
class DocumentControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestChunksUseCase ingestion;

    @MockBean
    private DeleteDocumentUseCase deletion;

    @Test
    void storesChunksFromStandardContract() throws Exception {
        when(ingestion.ingest(anyList(), org.mockito.ArgumentMatchers.anyString())).thenReturn(2);

        mockMvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"documentId":"manual.pdf","sectionPath":"Ch1","content":"brake torque is 120Nm",
                                   "metadata":{"lang":"en"},"aclTags":["engineering"]},
                                  {"documentId":"manual.pdf","content":"error ERR-503-GATEWAY"}
                                ]"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stored").value(2));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunk>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(ingestion).ingest(captor.capture(), org.mockito.ArgumentMatchers.anyString());
        List<DocumentChunk> sent = captor.getValue();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).aclTags()).containsExactly("engineering");
        assertThat(sent.get(1).aclTags()).containsExactly(DocumentChunk.PUBLIC);
        assertThat(sent.get(1).sectionPath()).isEmpty();
    }

    @Test
    void emptyArrayReturns400() throws Exception {
        mockMvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankContentReturns400() throws Exception {
        mockMvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"documentId\":\"d\",\"content\":\"  \"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturnsRemovedCount() throws Exception {
        when(deletion.delete(eq("law:gdpr"), eq(List.of()))).thenReturn(4);

        mockMvc.perform(delete("/api/documents/{id}", "law:gdpr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("law:gdpr"))
                .andExpect(jsonPath("$.deleted").value(4));
    }

    @Test
    void deleteOfUnknownDocumentIsIdempotentZero() throws Exception {
        when(deletion.delete(eq("missing"), eq(List.of()))).thenReturn(0);

        mockMvc.perform(delete("/api/documents/{id}", "missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(0));
    }

    @Test
    void deletePassesXRolesThrough() throws Exception {
        when(deletion.delete(eq("law:gdpr"), eq(List.of("legal")))).thenReturn(2);

        mockMvc.perform(delete("/api/documents/{id}", "law:gdpr").header("X-Roles", "legal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(2));

        verify(deletion).delete("law:gdpr", List.of("legal"));
    }

    @Test
    void deleteRefusedByRbacReturns403() throws Exception {
        when(deletion.delete(eq("eng-only"), eq(List.of())))
                .thenThrow(new DocumentStore.DeleteNotPermittedException("eng-only"));

        mockMvc.perform(delete("/api/documents/{id}", "eng-only"))
                .andExpect(status().isForbidden());
    }
}
