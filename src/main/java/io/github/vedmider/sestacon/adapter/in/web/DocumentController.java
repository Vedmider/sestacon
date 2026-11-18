package io.github.vedmider.sestacon.adapter.in.web;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.model.DocumentVersion;
import io.github.vedmider.sestacon.domain.port.in.DeleteDocumentUseCase;
import io.github.vedmider.sestacon.domain.port.in.DocumentVersionUseCase;
import io.github.vedmider.sestacon.domain.port.in.IngestChunksUseCase;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.logging.SecurityAudit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Source-agnostic ingestion: accepts chunks that already conform to the standard JSON
 * contract, so any external producer (Jira exporter, log shipper, ERP extract, a separate
 * parser service) can push data without going through Tika file parsing.
 *
 * <p>Ingesting a {@code documentId} that already exists <b>replaces</b> all its chunks
 * (supersession), so re-posting a document never appends duplicates. {@code DELETE} removes
 * a document entirely, subject to all-or-nothing RBAC.
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Ingest pre-parsed chunks in the standard JSON contract")
public class DocumentController {

    private final IngestChunksUseCase ingestion;
    private final DeleteDocumentUseCase deletion;
    private final DocumentVersionUseCase versions;

    public DocumentController(IngestChunksUseCase ingestion, DeleteDocumentUseCase deletion,
                              DocumentVersionUseCase versions) {
        this.ingestion = ingestion;
        this.deletion = deletion;
        this.versions = versions;
    }

    @PostMapping
    @Operation(summary = "Ingest chunks",
            description = "Accepts a JSON array of chunks {documentId, sectionPath, content, "
                    + "metadata, aclTags}. Chunks are embedded and stored. aclTags default to "
                    + "[\"public\"] when omitted. Returns 400 if any chunk has blank "
                    + "documentId or content.")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody List<ChunkRequest> chunks,
            @org.springframework.web.bind.annotation.RequestParam(value = "sourceSystem",
                    required = false) String sourceSystem) {
        if (chunks == null || chunks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No chunks provided");
        }
        List<DocumentChunk> domain = chunks.stream().map(ChunkRequest::toDomain).toList();
        var result = ingestion.ingestWithVersions(domain,
                sourceSystem == null || sourceSystem.isBlank() ? "unknown" : sourceSystem);
        log(domain);
        List<Map<String, Object>> changes = result.changes().stream()
                .map(d -> Map.<String, Object>of(
                        "documentId", d.documentId(),
                        "status", d.status().name(),
                        "changedSections", d.changedSections()))
                .toList();
        return ResponseEntity.ok(Map.of("stored", result.stored(), "changes", changes));
    }

    @DeleteMapping("/{documentId}")
    @Operation(summary = "Delete a document",
            description = "Removes all chunks of the given documentId (e.g. a superseded law). "
                    + "All-or-nothing RBAC: the caller's X-Roles must cover every chunk of the "
                    + "document, else 403 and nothing is deleted. Deleting an unknown id returns "
                    + "200 with deleted=0 (idempotent).")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String documentId,
            @RequestHeader(value = "X-Roles", required = false) List<String> roles) {
        try {
            int deleted = deletion.delete(documentId, roles == null ? List.of() : roles);
            return ResponseEntity.ok(Map.of("documentId", documentId, "deleted", deleted));
        }
        catch (DocumentStore.DeleteNotPermittedException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "insufficient roles to delete all chunks of document: " + documentId);
        }
    }

    @GetMapping("/{documentId}/version")
    @Operation(summary = "Document version",
            description = "Returns the stored version identity of a document — docHash, sizeBytes, "
                    + "chunkCount and per-section content hashes — so a caller can detect changes "
                    + "(and which sections changed) without re-uploading. 404 if unknown.")
    public ResponseEntity<Map<String, Object>> version(@PathVariable String documentId) {
        DocumentVersion v = versions.version(documentId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "no version for: " + documentId));
        return ResponseEntity.ok(Map.of(
                "documentId", v.documentId(),
                "docHash", v.docHash(),
                "sizeBytes", v.sizeBytes(),
                "chunkCount", v.chunkCount(),
                "sectionHashes", v.sectionHashes()));
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
