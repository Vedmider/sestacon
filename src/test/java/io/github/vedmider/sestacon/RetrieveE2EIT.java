package io.github.vedmider.sestacon;

import io.github.vedmider.sestacon.domain.port.out.ChatProvider;
import io.github.vedmider.sestacon.support.DeterministicEmbeddingModel;
import io.github.vedmider.sestacon.support.FakeChatProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * True HTTP end-to-end: a running app on a random port driven with TestRestTemplate against a real
 * pgvector DB. Covers ingest→retrieve over the wire, metadata filtering, and RBAC through HTTP.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.retrieval.mode=hybrid",
        "app.ingest.contextual=false",
        "spring.ai.vectorstore.pgvector.dimensions=384",
        "spring.autoconfigure.exclude=org.springframework.ai.model.transformers.autoconfigure.TransformersEmbeddingModelAutoConfiguration"
})
class RetrieveE2EIT {

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
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE rag_chunks");
    }

    private void ingest(String documentId, String content, String sourceSystem, String aclTag) {
        String acl = aclTag == null ? "[\"public\"]" : "[\"" + aclTag + "\"]";
        String body = "[{\"documentId\":\"" + documentId + "\",\"content\":\"" + content
                + "\",\"aclTags\":" + acl + "}]";
        ResponseEntity<String> r = rest.exchange(
                "/api/documents?sourceSystem=" + sourceSystem, HttpMethod.POST,
                jsonEntity(body, null), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private ResponseEntity<RetrievedChunkDto[]> retrieve(String json, String roles) {
        return rest.exchange("/api/retrieve", HttpMethod.POST, jsonEntity(json, roles),
                RetrievedChunkDto[].class);
    }

    private HttpEntity<String> jsonEntity(String body, String roles) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (roles != null) {
            h.add("X-Roles", roles);
        }
        return new HttpEntity<>(body, h);
    }

    @Test
    void ingestThenRetrieveWithMetadataFilterOverHttp() {
        ingest("a.md", "zorblax appears in a jira ticket", "jira", null);
        ingest("b.md", "zorblax appears in confluence", "confluence", null);

        RetrievedChunkDto[] jira = retrieve(
                "{\"query\":\"zorblax\",\"metadata\":{\"sourceSystem\":\"jira\"}}", null).getBody();

        assertThat(jira).extracting(RetrievedChunkDto::source).containsExactly("a.md");
    }

    @Test
    void derivedMetadataIsQueryableEndToEnd() {
        ingest("guide.md", "zorblax runbook incident rollback", "confluence", null);

        RetrievedChunkDto[] byType = retrieve(
                "{\"query\":\"zorblax\",\"metadata\":{\"docType\":\"md\"}}", null).getBody();

        assertThat(byType).extracting(RetrievedChunkDto::source).contains("guide.md");
    }

    @Test
    void noFilterReturnsEverythingOverHttp() {
        ingest("a.md", "zorblax one", "jira", null);
        ingest("b.md", "zorblax two", "confluence", null);

        RetrievedChunkDto[] all = retrieve("{\"query\":\"zorblax\"}", null).getBody();

        assertThat(all).extracting(RetrievedChunkDto::source).contains("a.md", "b.md");
    }

    // D6 — RBAC over HTTP: a filter match must never widen access.
    @Test
    void metadataFilterNeverWidensAccessPastRbac() {
        ingest("secret.md", "zorblax restricted", "jira", "engineering");

        RetrievedChunkDto[] asPublic = retrieve(
                "{\"query\":\"zorblax\",\"metadata\":{\"sourceSystem\":\"jira\"}}", null).getBody();
        assertThat(asPublic).isEmpty();

        RetrievedChunkDto[] asEng = retrieve(
                "{\"query\":\"zorblax\",\"metadata\":{\"sourceSystem\":\"jira\"}}", "engineering").getBody();
        assertThat(asEng).extracting(RetrievedChunkDto::source).contains("secret.md");
    }

    @Test
    void noRolesSeesOnlyPublicEvenWithMatchingFilter() {
        ingest("pub.md", "zorblax public", "jira", null);
        ingest("eng.md", "zorblax engineering", "jira", "engineering");

        RetrievedChunkDto[] asPublic = retrieve(
                "{\"query\":\"zorblax\",\"metadata\":{\"sourceSystem\":\"jira\"}}", null).getBody();

        assertThat(asPublic).extracting(RetrievedChunkDto::source)
                .contains("pub.md").doesNotContain("eng.md");
    }

    // D4 — contract: unknown request fields ignored; response keys stable.
    @Test
    void ignoresUnknownRequestFieldsAndKeepsResponseShape() {
        ingest("a.md", "zorblax content", "jira", null);

        ResponseEntity<String> raw = rest.exchange("/api/retrieve", HttpMethod.POST,
                jsonEntity("{\"query\":\"zorblax\",\"somethingNew\":123}", null), String.class);

        assertThat(raw.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(raw.getBody()).contains("\"text\"").contains("\"source\"").contains("\"score\"");
    }

    // D7 — negative: blank query -> 400 over HTTP.
    @Test
    void blankQueryReturns400OverHttp() {
        ResponseEntity<String> r = rest.exchange("/api/retrieve", HttpMethod.POST,
                jsonEntity("{\"query\":\"   \"}", null), String.class);
        assertThat(r.getStatusCode().value()).isEqualTo(400);
    }

    record RetrievedChunkDto(String text, String source, double score) {
    }
}
