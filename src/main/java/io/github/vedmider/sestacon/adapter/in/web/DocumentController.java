package io.github.vedmider.sestacon.adapter.in.web;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.port.in.IngestChunksUseCase;
import io.github.vedmider.sestacon.logging.SecurityAudit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Source-agnostic ingestion: accepts chunks that already conform to the standard JSON
 * contract, so any external producer (Jira exporter, log shipper, ERP extract, a separate
 * parser service) can push data without going through Tika file parsing.
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Ingest pre-parsed chunks in the standard JSON contract")
public class DocumentController {

    private final IngestChunksUseCase ingestion;

    public DocumentController(IngestChunksUseCase ingestion) {
        this.ingestion = ingestion;
    }

    @PostMapping
    @Operation(summary = "Ingest chunks",
            description = "Accepts a JSON array of chunks {documentId, sectionPath, content, "
                    + "metadata, aclTags}. Chunks are embedded and stored. aclTags default to "
                    + "[\"public\"] when omitted. Returns 400 if any chunk has blank "
                    + "documentId or content.")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody List<ChunkRequest> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No chunks provided");
        }
        List<DocumentChunk> domain = chunks.stream().map(ChunkRequest::toDomain).toList();
        int stored = ingestion.ingest(domain);
        log(domain);
        return ResponseEntity.ok(Map.of("stored", stored));
    }

    private static void log(List<DocumentChunk> chunks) {
        chunks.stream().map(DocumentChunk::documentId).distinct()
                .forEach(id -> SecurityAudit.chunksIngested(id));
    }

    public record ChunkRequest(String documentId, String sectionPath, String content,
                               Map<String, Object> metadata, List<String> aclTags) {

        DocumentChunk toDomain() {
            if (!StringUtils.hasText(documentId) || !StringUtils.hasText(content)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "documentId and content must not be blank");
            }
            return new DocumentChunk(documentId,
                    sectionPath == null ? "" : sectionPath,
                    content, metadata, aclTags);
        }
    }
}
