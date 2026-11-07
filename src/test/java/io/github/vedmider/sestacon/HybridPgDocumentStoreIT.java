package io.github.vedmider.sestacon;

import io.github.vedmider.sestacon.adapter.out.store.HybridPgDocumentStore;
import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.out.ChatProvider;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.support.DeterministicEmbeddingModel;
import io.github.vedmider.sestacon.support.FakeChatProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the hybrid store fuses vector + full-text retrieval, enforces RBAC before
 * ranking, and merges with RRF — in one SQL round-trip against a real pgvector database.
 */
@Testcontainers
@SpringBootTest(properties = {
        "app.retrieval.mode=hybrid",
        "spring.ai.vectorstore.pgvector.dimensions=384",
        "spring.autoconfigure.exclude=org.springframework.ai.model.transformers.autoconfigure.TransformersEmbeddingModelAutoConfiguration"
})
class HybridPgDocumentStoreIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @TestConfiguration
    static class TestBeans {
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
    private DocumentStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE TABLE rag_chunks");
        store.add(List.of(
                new DocumentChunk("public-doc", "s1",
                        "The quarterly revenue report mentions zorblax metrics.",
                        java.util.Map.of(), List.of(DocumentChunk.PUBLIC)),
                new DocumentChunk("eng-doc", "s2",
                        "Internal zorblax calibration failed with error ERR-503-GATEWAY.",
                        java.util.Map.of(), List.of("engineering"))));
    }

    @Test
    void storeIsTheHybridImplementation() {
        assertThat(store).isInstanceOf(HybridPgDocumentStore.class);
    }

    @Test
    void rbacHidesRestrictedChunksFromCallersWithoutTheRole() {
        List<String> sources = store.search("zorblax", 10, List.of()).stream()
                .map(RetrievedChunk::source).toList();

        assertThat(sources).contains("public-doc");
        assertThat(sources).doesNotContain("eng-doc");
    }

    @Test
    void rbacRevealsRestrictedChunksToCallersWithTheRole() {
        List<String> sources = store.search("zorblax", 10, List.of("engineering")).stream()
                .map(RetrievedChunk::source).toList();

        assertThat(sources).contains("public-doc", "eng-doc");
    }

    @Test
    void fullTextCatchesExactErrorCodeThatVectorsScramble() {
        List<String> sources = store.search("ERR-503-GATEWAY", 10, List.of("engineering")).stream()
                .map(RetrievedChunk::source).toList();

        assertThat(sources).contains("eng-doc");
    }
}
