package io.github.vedmider.sestacon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.ingest")
public class IngestProperties {

    private long maxFileSizeBytes = 10L * 1024 * 1024;
    private List<String> allowedContentTypes = List.of(
            // text
            "text/plain", "text/markdown", "text/x-markdown", "text/html", "text/csv",
            // structured (naive text chunking)
            "application/json", "application/x-yaml", "application/yaml", "text/yaml",
            // documents (Apache Tika extracts text)
            "application/pdf", "application/rtf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text");
            // NOTE: image types are intentionally NOT allow-listed — image ingestion is disabled
            // (no OCR backend). The surrogate/OCR path in TikaDocumentParser is retained; re-enable by
            // adding image/png, image/jpeg, ... here (and configuring a tesseract backend).

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }
}
