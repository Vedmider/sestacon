package io.github.vedmider.sestacon.adapter.in.web;

import io.github.vedmider.sestacon.config.IngestProperties;
import io.github.vedmider.sestacon.domain.port.in.DocumentIngestionUseCase;
import io.github.vedmider.sestacon.logging.SecurityAudit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Inbound HTTP adapter for document ingestion. Validates input, delegates to the use case. */
@RestController
@RequestMapping("/api/ingest")
@Tag(name = "Ingest", description = "Upload documents to be parsed, chunked, embedded and stored")
public class IngestController {

    private static final Logger log = LoggerFactory.getLogger(IngestController.class);

    private final DocumentIngestionUseCase ingestion;
    private final IngestProperties properties;

    public IngestController(DocumentIngestionUseCase ingestion, IngestProperties properties) {
        this.ingestion = ingestion;
        this.properties = properties;
    }

    @PostMapping
    @Operation(summary = "Upload documents",
            description = "Accepts one or more files (multipart field 'files'). Each is parsed "
                    + "with Tika, chunked, embedded locally and stored. The optional 'aclTags' "
                    + "field restricts retrieval to callers whose X-Roles intersect it (default: "
                    + "public). 400/413/415 on invalid input.")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "aclTags", required = false) List<String> aclTags,
            @RequestParam(value = "sourceSystem", required = false) String sourceSystem) {
        if (files.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No files provided");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        int totalChunks = 0;

        for (MultipartFile file : files) {
            validate(file);
            try {
                int chunks = ingestion.ingest(filename(file), file.getBytes(),
                        aclTags == null ? List.of() : aclTags,
                        sourceSystem == null || sourceSystem.isBlank() ? "unknown" : sourceSystem);
                totalChunks += chunks;
                results.add(Map.of("file", filename(file), "chunks", chunks));
                log.info("event=ingest_ok file=\"{}\" size={} chunks={}",
                        filename(file), file.getSize(), chunks);
            }
            catch (IOException e) {
                SecurityAudit.ingestRejected(filename(file), "unreadable");
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Failed to read file " + filename(file), e);
            }
        }

        return ResponseEntity.ok(Map.of("results", results, "totalChunks", totalChunks));
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            SecurityAudit.ingestRejected(filename(file), "empty");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty file: " + filename(file));
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            SecurityAudit.ingestRejected(filename(file), "too_large");
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "File exceeds max size of " + properties.getMaxFileSizeBytes() + " bytes: "
                            + filename(file));
        }
        String contentType = file.getContentType();
        if (contentType == null || !properties.getAllowedContentTypes().contains(contentType)) {
            SecurityAudit.ingestRejected(filename(file), "unsupported_type:" + contentType);
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported or missing content type '" + contentType + "'. Allowed: "
                            + properties.getAllowedContentTypes());
        }
    }

    private static String filename(MultipartFile file) {
        String name = file.getOriginalFilename();
        return StringUtils.hasText(name) ? name : "upload";
    }
}
