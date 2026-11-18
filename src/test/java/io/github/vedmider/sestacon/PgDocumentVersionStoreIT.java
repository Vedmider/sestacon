package io.github.vedmider.sestacon;

import io.github.vedmider.sestacon.domain.model.DocumentDiff;
import io.github.vedmider.sestacon.domain.model.DocumentVersion;
import io.github.vedmider.sestacon.domain.port.out.ChatProvider;
import io.github.vedmider.sestacon.domain.port.out.DocumentVersionStore;
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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves version identity tracking (hashes/size) and diff classification against a real database. */
@Testcontainers
@SpringBootTest(properties = {
        "app.retrieval.mode=hybrid",
        "spring.ai.vectorstore.pgvector.dimensions=384",
        "spring.autoconfigure.exclude=org.springframework.ai.model.transformers.autoconfigure.TransformersEmbeddingModelAutoConfiguration"
})
class PgDocumentVersionStoreIT {

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
    private DocumentVersionStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE rag_documents CASCADE");
    }

    private static Map<String, String> sections(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void diffOfUnknownDocumentIsNew() {
        assertThat(store.diff("doc", sections("c0", "h0")).status())
                .isEqualTo(DocumentDiff.Status.NEW);
    }

    @Test
    void recordThenFindRoundTripsHashesSizeAndCount() {
        store.record("doc", "DOCHASH", 1234L, sections("c0", "h0", "c1", "h1"));

        DocumentVersion v = store.find("doc").orElseThrow();
        assertThat(v.docHash()).isEqualTo("DOCHASH");
        assertThat(v.sizeBytes()).isEqualTo(1234L);
        assertThat(v.chunkCount()).isEqualTo(2);
        assertThat(v.sectionHashes()).containsEntry("c0", "h0").containsEntry("c1", "h1");
    }

    @Test
    void identicalReRecordDiffIsUnchanged() {
        store.record("doc", "H", 10L, sections("c0", "h0", "c1", "h1"));
        assertThat(store.diff("doc", sections("c0", "h0", "c1", "h1")).status())
                .isEqualTo(DocumentDiff.Status.UNCHANGED);
    }

    @Test
    void changedSectionIsPartialAndNamed() {
        store.record("doc", "H", 10L, sections("c0", "h0", "c1", "h1"));

        DocumentDiff d = store.diff("doc", sections("c0", "h0", "c1", "CHANGED"));
        assertThat(d.status()).isEqualTo(DocumentDiff.Status.PARTIAL);
        assertThat(d.changedSections()).containsExactly("c1");
    }

    @Test
    void reRecordReplacesSectionRowsRatherThanAppending() {
        store.record("doc", "H1", 10L, sections("c0", "h0", "c1", "h1"));
        store.record("doc", "H2", 20L, sections("c0", "h0"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM rag_document_chunks WHERE document_id = 'doc'", Integer.class))
                .isEqualTo(1);
        assertThat(store.find("doc").orElseThrow().chunkCount()).isEqualTo(1);
    }

    @Test
    void deleteRemovesVersionAndCascadesChunkRows() {
        store.record("doc", "H", 10L, sections("c0", "h0"));

        assertThat(store.delete("doc")).isEqualTo(1);
        assertThat(store.find("doc")).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM rag_document_chunks WHERE document_id = 'doc'", Integer.class))
                .isZero();
    }
}
