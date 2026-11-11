package io.github.vedmider.sestacon.adapter.out.enrich;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleMetadataEnricherTest {

    private final RuleMetadataEnricher enricher = new RuleMetadataEnricher();

    private DocumentChunk enrichOne(String documentId, String content, String source) {
        return enricher.enrich(List.of(DocumentChunk.of(documentId, content)), source).get(0);
    }

    @Test
    void derivesDocTypeFromDocumentIdExtension() {
        assertThat(enrichOne("runbook.md", "x", "s").metadata()).containsEntry("docType", "md");
        assertThat(enrichOne("report.PDF", "x", "s").metadata()).containsEntry("docType", "pdf");
    }

    @Test
    void docTypeUnknownWhenNoExtension() {
        assertThat(enrichOne("explain:repo:path", "x", "s").metadata())
                .containsEntry("docType", "unknown");
    }

    @Test
    void recordsSourceSystemAndDefaultsToUnknown() {
        assertThat(enrichOne("a.md", "x", "snowflake").metadata())
                .containsEntry("sourceSystem", "snowflake");
        assertThat(enrichOne("a.md", "x", null).metadata())
                .containsEntry("sourceSystem", "unknown");
    }

    @Test
    void addsIngestedAtTimestamp() {
        assertThat(enrichOne("a.md", "x", "s").metadata()).containsKey("ingestedAt");
    }

    @Test
    void tagsContentByKeywordRules() {
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) enrichOne(
                "d.md", "On incident, follow the rollback runbook to mitigate.", "s")
                .metadata().get("tags");
        assertThat(tags).contains("runbook");
    }

    @Test
    void proseWithoutKeywordsGetsNoTags() {
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) enrichOne(
                "d.md", "A gentle description of the weather today.", "s").metadata().get("tags");
        assertThat(tags).isEmpty();
    }

    @Test
    void guessesEnglishFromStopwords() {
        assertThat(enrichOne("d.md", "the service and the store are ready for this", "s")
                .metadata()).containsEntry("lang", "en");
    }

    @Test
    void doesNotOverwriteCallerSuppliedKeys() {
        DocumentChunk given = new DocumentChunk("d.md", "", "incident rollback",
                Map.of("docType", "custom", "sourceSystem", "jira"), List.of("public"));
        DocumentChunk out = enricher.enrich(List.of(given), "snowflake").get(0);
        assertThat(out.metadata()).containsEntry("docType", "custom");      // preserved
        assertThat(out.metadata()).containsEntry("sourceSystem", "jira");    // preserved
        assertThat(out.metadata()).containsKey("tags");                      // still added
    }
}
