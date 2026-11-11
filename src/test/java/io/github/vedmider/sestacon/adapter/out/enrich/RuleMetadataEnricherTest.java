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

    @Test
    void docTypeEdgeCases() {
        assertThat(enrichOne("archive.tar.gz", "x", "s").metadata()).containsEntry("docType", "gz");
        assertThat(enrichOne("NOEXTENSION", "x", "s").metadata()).containsEntry("docType", "unknown");
        assertThat(enrichOne("README.MD", "x", "s").metadata()).containsEntry("docType", "md");
        assertThat(enrichOne("trailingdot.", "x", "s").metadata()).containsEntry("docType", "unknown");
        assertThat(enrichOne("explain:repo:docs/a.md", "x", "s").metadata())
                .containsEntry("docType", "md");
    }

    @Test
    void langBoundaryNeedsTwoStopwords() {
        assertThat(enrichOne("d.md", "the cat sleeps", "s").metadata())
                .containsEntry("lang", "unknown");                            // 1 stopword
        assertThat(enrichOne("d.md", "the cat and the dog", "s").metadata())
                .containsEntry("lang", "en");                                 // 2 stopwords
    }

    @Test
    void conflictingTagRulesYieldAllMatchingTagsDeterministically() {
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) enrichOne("d.md",
                "the auth endpoint request needs a token during the incident rollback", "s")
                .metadata().get("tags");
        assertThat(tags).contains("api", "security", "runbook");
    }

    @Test
    void everyDerivedKeyIsPreservedWhenCallerSetsIt() {
        DocumentChunk given = new DocumentChunk("d.md", "", "the and for incident",
                Map.of("docType", "x", "lang", "fr", "sourceSystem", "jira",
                        "ingestedAt", "2000-01-01T00:00:00Z", "tags", List.of("manual")),
                List.of("public"));
        DocumentChunk out = enricher.enrich(List.of(given), "snowflake").get(0);
        assertThat(out.metadata()).containsEntry("docType", "x").containsEntry("lang", "fr")
                .containsEntry("sourceSystem", "jira").containsEntry("ingestedAt", "2000-01-01T00:00:00Z")
                .containsEntry("tags", List.of("manual"));
    }
}
