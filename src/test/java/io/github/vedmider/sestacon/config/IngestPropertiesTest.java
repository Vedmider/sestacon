package io.github.vedmider.sestacon.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IngestPropertiesTest {

    private final IngestProperties properties = new IngestProperties();

    @Test
    void defaultAllowlistCoversTextDocumentAndStructuredTypes() {
        assertThat(properties.getAllowedContentTypes()).contains(
                "text/plain", "text/markdown", "text/html", "text/csv",
                "application/json", "application/x-yaml", "text/yaml",
                "application/pdf", "application/rtf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.oasis.opendocument.text");
    }

    @Test
    void imagesArchivesAndBinariesStayExcludedSoTheyReturn415() {
        // image ingestion is disabled (no OCR backend); the parser code is retained but not allow-listed
        assertThat(properties.getAllowedContentTypes())
                .doesNotContain("image/png", "image/jpeg", "application/zip", "application/octet-stream");
    }
}
