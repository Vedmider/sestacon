package com.example.rag.domain.model;

import java.util.List;

/**
 * Prepends a document/section header to each chunk's content so the embedded and returned text
 * carries its enclosing context (which document and section it came from). This is the lightweight
 * form of parent-document retrieval: the child chunk stays small but is no longer context-free.
 */
public final class ContextualChunker {

    private ContextualChunker() {
    }

    public static List<DocumentChunk> contextualize(List<DocumentChunk> chunks) {
        return chunks.stream().map(ContextualChunker::one).toList();
    }

    static DocumentChunk one(DocumentChunk c) {
        String header = header(c.documentId(), c.sectionPath());
        if (header.isEmpty() || c.content().startsWith(header)) {
            return c;
        }
        return new DocumentChunk(c.documentId(), c.sectionPath(),
                header + "\n" + c.content(), c.metadata(), c.aclTags());
    }

    private static String header(String documentId, String sectionPath) {
        boolean hasDoc = documentId != null && !documentId.isBlank();
        boolean hasSection = sectionPath != null && !sectionPath.isBlank();
        if (hasDoc && hasSection) {
            return "[" + documentId + " > " + sectionPath + "]";
        }
        if (hasDoc) {
            return "[" + documentId + "]";
        }
        if (hasSection) {
            return "[" + sectionPath + "]";
        }
        return "";
    }
}
