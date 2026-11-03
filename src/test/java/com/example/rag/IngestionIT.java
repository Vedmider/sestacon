package com.example.rag;

import com.example.rag.domain.port.out.ChatProvider;
import com.example.rag.support.DeterministicEmbeddingModel;
import com.example.rag.support.FakeChatProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.ai.vectorstore.pgvector.dimensions=384",
        "spring.autoconfigure.exclude=org.springframework.ai.model.transformers.autoconfigure.TransformersEmbeddingModelAutoConfiguration"
})
class IngestionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @TestConfiguration
    static class EmbeddingConfig {
        @Bean
        EmbeddingModel embeddingModel() {
            return new DeterministicEmbeddingModel(384);
        }

        @Bean
        ChatProvider chatProvider() {
            return new FakeChatProvider("ok");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void uploadsDocumentAndStoresChunks() throws Exception {
        String body = """
                Kubernetes is an open-source container orchestration platform.
                It automates deployment, scaling, and management of containerized applications.
                Helm is the package manager for Kubernetes, using charts to define releases.
                pgvector provides vector similarity search inside PostgreSQL.
                """;
        MockMultipartFile file = new MockMultipartFile(
                "files", "k8s.txt", MediaType.TEXT_PLAIN_VALUE,
                body.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/ingest").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChunks").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.results[0].file").value("k8s.txt"));

        Integer rows = jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Integer.class);
        assertThat(rows).isGreaterThan(0);

        Integer fromSource = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'source' = 'k8s.txt'",
                Integer.class);
        assertThat(fromSource).isGreaterThan(0);
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "evil.exe", "application/octet-stream", new byte[] {0x4d, 0x5a});

        mockMvc.perform(multipart("/api/ingest").file(file))
                .andExpect(status().isUnsupportedMediaType());
    }
}
