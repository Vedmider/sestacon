package io.github.vedmider.sestacon;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.in.IngestChunksUseCase;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Enrichment lands derived metadata at ingest; metadata-filtered retrieval honors it and RBAC. */
@Testcontainers
@SpringBootTest(properties = {
        "app.retrieval.mode=hybrid",
        "app.ingest.contextual=false",
        "spring.ai.vectorstore.pgvector.dimensions=384",
        "spring.autoconfigure.exclude=org.springframework.ai.model.transformers.autoconfigure.TransformersEmbeddingModelAutoConfiguration"
})
class MetadataEnrichmentIT {

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
    private IngestChunksUseCase ingest;

    @Autowired
    private DocumentStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE rag_chunks");
    }

    @Test
    void ingestDerivesAndStoresMetadata() {
        ingest.ingest(List.of(DocumentChunk.of("runbook.md",
                "On incident follow the rollback runbook to mitigate the zorblax outage.")),
                "confluence");

        String docType = jdbc.queryForObject(
                "SELECT metadata->>'docType' FROM rag_chunks LIMIT 1", String.class);
        String source = jdbc.queryForObject(
                "SELECT metadata->>'sourceSystem' FROM rag_chunks LIMIT 1", String.class);
        assertThat(docType).isEqualTo("md");
        assertThat(source).isEqualTo("confluence");
    }

    @Test
    void metadataFilterReturnsOnlyMatchingChunks() {
        ingest.ingest(List.of(DocumentChunk.of("a.md", "zorblax appears in a runbook incident")),
                "confluence");
        ingest.ingest(List.of(DocumentChunk.of("b.md", "zorblax appears in a jira ticket")),
                "jira");

        List<RetrievedChunk> onlyJira =
                store.search("zorblax", 10, List.of(), Map.of("sourceSystem", "jira"));

        assertThat(onlyJira).extracting(RetrievedChunk::source).containsExactly("b.md");
    }

    @Test
    void noFilterReturnsEverything() {
        ingest.ingest(List.of(DocumentChunk.of("a.md", "zorblax one")), "confluence");
        ingest.ingest(List.of(DocumentChunk.of("b.md", "zorblax two")), "jira");

        List<RetrievedChunk> all = store.search("zorblax", 10, List.of());

        assertThat(all).extracting(RetrievedChunk::source).contains("a.md", "b.md");
    }

    @Test
    void metadataFilterStillEnforcesRbac() {
        ingest.ingest(List.of(new DocumentChunk("secret.md", "", "zorblax restricted runbook",
                Map.of(), List.of("engineering"))), "confluence");

        List<RetrievedChunk> asPublic =
                store.search("zorblax", 10, List.of(), Map.of("sourceSystem", "confluence"));

        assertThat(asPublic).isEmpty();   // metadata matches, but RBAC hides it
    }

    @Test
    void multiKeyFilterIsAndSemantics() {
        ingest.ingest(List.of(DocumentChunk.of("a.md", "zorblax in jira md")), "jira");
        ingest.ingest(List.of(DocumentChunk.of("b.pdf", "zorblax in jira pdf")), "jira");

        List<RetrievedChunk> jiraMd = store.search("zorblax", 10, List.of(),
                Map.of("sourceSystem", "jira", "docType", "md"));

        assertThat(jiraMd).extracting(RetrievedChunk::source).containsExactly("a.md");
    }

    @Test
    void filterValueMismatchReturnsEmpty() {
        ingest.ingest(List.of(DocumentChunk.of("a.md", "zorblax in jira")), "jira");

        List<RetrievedChunk> none =
                store.search("zorblax", 10, List.of(), Map.of("sourceSystem", "nope"));

        assertThat(none).isEmpty();
    }

    @Test
    void sqlishFilterValueIsInjectionSafe() {
        ingest.ingest(List.of(DocumentChunk.of("a.md", "zorblax here")), "jira");

        List<RetrievedChunk> result = store.search("zorblax", 10, List.of(),
                Map.of("sourceSystem", "'; DROP TABLE rag_chunks; --"));

        assertThat(result).isEmpty();   // no match, and the table still exists (next query proves it)
        assertThat(store.search("zorblax", 10, List.of())).isNotEmpty();
    }
}
