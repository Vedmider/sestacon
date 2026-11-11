package io.github.vedmider.sestacon.adapter.in.web;

import io.github.vedmider.sestacon.config.IngestProperties;
import io.github.vedmider.sestacon.domain.port.in.DocumentIngestionUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Wire-contract test for POST /api/ingest: response schema {@code {totalChunks, results[]}}. */
@WebMvcTest(controllers = IngestController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = io.github.vedmider.sestacon.ratelimit.WebConfig.class))
class IngestApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentIngestionUseCase ingestion;

    @MockBean
    private IngestProperties properties;

    @BeforeEach
    void setup() {
        when(properties.getMaxFileSizeBytes()).thenReturn(1_000_000L);
        when(properties.getAllowedContentTypes()).thenReturn(List.of("text/plain"));
        when(ingestion.ingest(any(), any(), any(), any())).thenReturn(7);
    }

    @Test
    void successResponseMatchesContract() throws Exception {
        mockMvc.perform(multipart("/api/ingest")
                        .file(new MockMultipartFile("files", "a.txt", "text/plain", "hi".getBytes())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalChunks").isNumber())
                .andExpect(jsonPath("$.totalChunks").value(7))
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results[0].file").value("a.txt"))
                .andExpect(jsonPath("$.results[0].chunks").value(7));
    }
}
