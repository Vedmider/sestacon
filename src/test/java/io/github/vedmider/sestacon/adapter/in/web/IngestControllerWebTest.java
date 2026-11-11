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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IngestController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = io.github.vedmider.sestacon.ratelimit.WebConfig.class))
class IngestControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentIngestionUseCase ingestion;

    @MockBean
    private IngestProperties properties;

    @BeforeEach
    void setup() {
        when(properties.getMaxFileSizeBytes()).thenReturn(1024L);
        when(properties.getAllowedContentTypes()).thenReturn(List.of("text/plain", "application/pdf"));
        when(ingestion.ingest(any(), any(), any(), any())).thenReturn(3);
    }

    private MockMultipartFile file(String name, String type, byte[] bytes) {
        return new MockMultipartFile("files", name, type, bytes);
    }

    @Test
    void multipleFilesReturnPerFileAndTotalChunks() throws Exception {
        mockMvc.perform(multipart("/api/ingest")
                        .file(file("a.txt", "text/plain", "hello".getBytes()))
                        .file(file("b.txt", "text/plain", "world".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChunks").value(6))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].chunks").value(3));
    }

    @Test
    void bindsSourceSystemParamAndPassesItThrough() throws Exception {
        org.mockito.Mockito.when(ingestion.ingest(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("snowflake"))).thenReturn(3);

        mockMvc.perform(multipart("/api/ingest")
                        .file(file("a.txt", "text/plain", "hello".getBytes()))
                        .param("sourceSystem", "snowflake"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(ingestion).ingest(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("snowflake"));
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/ingest").file(file("empty.txt", "text/plain", new byte[0])))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedFileReturns413() throws Exception {
        mockMvc.perform(multipart("/api/ingest").file(file("big.txt", "text/plain", new byte[2048])))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mockMvc.perform(multipart("/api/ingest").file(file("x.zip", "application/zip", "PK".getBytes())))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void missingFilesPartReturns4xx() throws Exception {
        mockMvc.perform(multipart("/api/ingest").contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().is4xxClientError());
    }
}
