package io.github.vedmider.sestacon.domain.model;

import java.util.List;
import java.util.Map;

/**
 * The standard ingestion contract — a provider-agnostic chunk that any source (file
 * upload, Jira, logs, ERP, …) produces. The search engine consumes exactly this shape.
 *
 * @param documentId  stable id of the source document (used as the citation source)
 * @param sectionPath logical location within the document (e.g. "Chapter 2 > Limits")
 * @param content     the chunk text to embed and search
 * @param metadata    arbitrary extra fields (stored as JSON, available for filtering)
 * @param aclTags     roles/groups allowed to retrieve this chunk ("public" = everyone)
 */
public record DocumentChunk(String documentId, String sectionPath, String content,
                            Map<String, Object> metadata, List<String> aclTags) {

    public static final String PUBLIC = "public";

    public DocumentChunk {
        metadata = metadata == null ? Map.of() : metadata;
        aclTags = (aclTags == null || aclTags.isEmpty()) ? List.of(PUBLIC) : aclTags;
    }

    /** Convenience for simple sources: a public chunk with only an id and content. */
    public static DocumentChunk of(String documentId, String content) {
        return new DocumentChunk(documentId, "", content, Map.of(), List.of(PUBLIC));
    }
}
