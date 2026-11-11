package io.github.vedmider.sestacon;

import io.github.vedmider.sestacon.application.eval.EvalHarness;
import io.github.vedmider.sestacon.application.eval.EvalHarness.Case;
import io.github.vedmider.sestacon.application.eval.EvalHarness.Report;
import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.in.IngestChunksUseCase;
import io.github.vedmider.sestacon.domain.port.in.RetrieveUseCase;
import io.github.vedmider.sestacon.domain.port.out.ChatProvider;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end retrieval-quality gate: replays labelled cases through the real hybrid retriever
 * over pgvector and asserts P@k / recall@k / MRR above measured-baseline thresholds, so a
 * ranking or filtering regression fails {@code mvn verify} instead of shipping silently.
 */
@Testcontainers
@SpringBootTest(properties = {
        "app.retrieval.mode=hybrid",
        "app.ingest.contextual=false",
        "spring.ai.vectorstore.pgvector.dimensions=384",
        "spring.autoconfigure.exclude=org.springframework.ai.model.transformers.autoconfigure.TransformersEmbeddingModelAutoConfiguration"
})
class EvalHarnessIT {

    private static final int TOP_K = 5;
    private static final double MIN_PRECISION = 0.20;
    private static final double MIN_RECALL = 0.80;
    private static final double MIN_MRR = 0.80;

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
    private RetrieveUseCase retrieve;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE TABLE rag_chunks");
        ingest.ingest(List.of(
                DocumentChunk.of("rollback-runbook.md",
                        "To roll back a failed deployment, scale the old replicaset up and the new one down, then verify health checks."),
                DocumentChunk.of("scaling-runbook.md",
                        "Horizontal pod autoscaling adds replicas when CPU exceeds the target; configure min and max replica counts."),
                DocumentChunk.of("auth-api.md",
                        "The authentication API issues a bearer token from the login endpoint; include it in the Authorization header."),
                DocumentChunk.of("rate-limit-api.md",
                        "The public API enforces a rate limit of one hundred requests per minute per API key; exceeding it returns 429."),
                DocumentChunk.of("backup-guide.md",
                        "Nightly database backups are written to object storage and retained for thirty days before expiry."),
                DocumentChunk.of("restore-guide.md",
                        "To restore from backup, download the latest snapshot from object storage and replay it into a fresh database."),
                DocumentChunk.of("tls-config.md",
                        "Configure TLS by mounting the certificate and private key, then set the minimum protocol version to 1.2."),
                DocumentChunk.of("logging-config.md",
                        "Set the log level with an environment variable; structured JSON logs are shipped to the aggregation pipeline."),
                DocumentChunk.of("onboarding.md",
                        "New engineers request repository access, install the toolchain, and run the local stack before their first change."),
                DocumentChunk.of("incident-policy.md",
                        "During an incident, the on-call engineer declares severity, opens a bridge, and records a timeline for the postmortem.")),
                "confluence");
    }

    private List<Case> cases() {
        return List.of(
                new Case("how do I roll back a bad deployment", Set.of("rollback-runbook.md")),
                new Case("how does autoscaling add replicas", Set.of("scaling-runbook.md")),
                new Case("how do I get an auth bearer token", Set.of("auth-api.md")),
                new Case("what is the API rate limit", Set.of("rate-limit-api.md")),
                new Case("how do I restore the database from backup", Set.of("restore-guide.md")),
                new Case("how do I configure TLS certificates", Set.of("tls-config.md")));
    }

    @Test
    void realRetrievalMeetsQualityThresholds() {
        Report report = new EvalHarness(retrieve).run(cases(), TOP_K, List.of());
        System.out.println("[eval] baseline\n" + report.toMarkdown());

        assertThat(report.meanPrecisionAtK()).isGreaterThanOrEqualTo(MIN_PRECISION);
        assertThat(report.meanRecallAtK()).isGreaterThanOrEqualTo(MIN_RECALL);
        assertThat(report.mrr()).isGreaterThanOrEqualTo(MIN_MRR);
    }

    @Test
    void retrievalIsDeterministicAcrossRuns() {
        List<String> first = sourcesFor("how do I roll back a bad deployment");
        List<String> second = sourcesFor("how do I roll back a bad deployment");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void authorizationAwareRetrievalHidesRestrictedDocFromWrongRole() {
        jdbc.execute("TRUNCATE TABLE rag_chunks");
        ingest.ingest(List.of(new DocumentChunk("secret-runbook.md", "",
                "Classified rollback procedure for the restricted production cluster.",
                java.util.Map.of(), List.of("sre"))), "confluence");

        var cases = List.of(new Case("restricted rollback procedure", Set.of("secret-runbook.md")));

        Report asPublic = new EvalHarness(retrieve).run(cases, TOP_K, List.of());
        Report asSre = new EvalHarness(retrieve).run(cases, TOP_K, List.of("sre"));

        assertThat(asPublic.meanRecallAtK()).isZero();
        assertThat(asSre.meanRecallAtK()).isCloseTo(1.0, org.assertj.core.api.Assertions.within(1e-9));
    }

    private List<String> sourcesFor(String query) {
        return retrieve.retrieve(query, TOP_K, List.of()).stream()
                .map(RetrievedChunk::source).toList();
    }
}
