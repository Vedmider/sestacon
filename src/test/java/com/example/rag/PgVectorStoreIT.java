package com.example.rag;

import com.example.rag.domain.port.out.ChatProvider;
import com.example.rag.support.DeterministicEmbeddingModel;
import com.example.rag.support.FakeChatProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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

@Testcontainers
@SpringBootTest(properties = {
        "spring.ai.vectorstore.pgvector.dimensions=384",
        "spring.autoconfigure.exclude=org.springframework.ai.model.transformers.autoconfigure.TransformersEmbeddingModelAutoConfiguration"
})
class PgVectorStoreIT {

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
    private VectorStore vectorStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void schemaIsInitialized() {
        Integer tables = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'vector_store'",
                Integer.class);
        assertThat(tables).isEqualTo(1);
    }

    @Test
    void storesAndCosineSearchesDocuments() {
        vectorStore.add(List.of(
                new Document("Kubernetes orchestrates containers across a cluster."),
                new Document("Espresso is a concentrated form of coffee."),
                new Document("pgvector adds vector similarity search to PostgreSQL.")));

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("pgvector adds vector similarity search to PostgreSQL.")
                        .topK(1)
                        .build());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getText())
                .isEqualTo("pgvector adds vector similarity search to PostgreSQL.");
    }
}
