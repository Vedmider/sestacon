package io.github.vedmider.sestacon.adapter.out.enrich;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.port.out.MetadataEnricher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, rule-based enrichment (no LLM): docType from the documentId extension, a cheap
 * language guess, keyword-rule content tags, source-system provenance, and an ingest timestamp.
 * Keys already present on a chunk are never overwritten — the producer stays authoritative.
 */
@Component
public class RuleMetadataEnricher implements MetadataEnricher {

    static final String UNKNOWN = "unknown";

    // Ordered: first matching tag per rule; precision-first keyword sets.
    private static final Map<String, Set<String>> TAG_RULES = Map.of(
            "runbook", Set.of("runbook", "incident", "oncall", "mitigation", "rollback"),
            "api", Set.of("endpoint", "request", "response", "payload", "http", "rest"),
            "security", Set.of("auth", "rbac", "permission", "secret", "token", "encrypt"),
            "config", Set.of("configuration", "property", "env var", "setting", "yaml"),
            "architecture", Set.of("architecture", "component", "module", "diagram", "boundary"));

    private static final Set<String> EN_STOPWORDS =
            Set.of("the", "and", "for", "with", "that", "this", "from", "are", "was");

    @Override
    public List<DocumentChunk> enrich(List<DocumentChunk> chunks, String sourceSystem) {
        String source = sourceSystem == null || sourceSystem.isBlank() ? UNKNOWN : sourceSystem;
        String ingestedAt = Instant.now().toString();
        List<DocumentChunk> out = new ArrayList<>(chunks.size());
        for (DocumentChunk c : chunks) {
            out.add(enrichOne(c, source, ingestedAt));
        }
        return out;
    }

    private DocumentChunk enrichOne(DocumentChunk c, String source, String ingestedAt) {
        Map<String, Object> meta = new LinkedHashMap<>(c.metadata());
        meta.putIfAbsent("docType", docType(c.documentId()));
        meta.putIfAbsent("lang", lang(c.content()));
        meta.putIfAbsent("sourceSystem", source);
        meta.putIfAbsent("ingestedAt", ingestedAt);
        meta.putIfAbsent("tags", tags(c.content()));
        return new DocumentChunk(c.documentId(), c.sectionPath(), c.content(), meta, c.aclTags());
    }

    static String docType(String documentId) {
        if (documentId == null) {
            return UNKNOWN;
        }
        int dot = documentId.lastIndexOf('.');
        int slash = Math.max(documentId.lastIndexOf('/'), documentId.lastIndexOf(':'));
        if (dot > slash && dot < documentId.length() - 1) {
            return documentId.substring(dot + 1).toLowerCase();
        }
        return UNKNOWN;
    }

    static String lang(String content) {
        if (content == null || content.isBlank()) {
            return UNKNOWN;
        }
        String lower = content.toLowerCase();
        long hits = EN_STOPWORDS.stream().filter(w -> lower.contains(" " + w + " ")).count();
        return hits >= 2 ? "en" : UNKNOWN;
    }

    static List<String> tags(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String lower = content.toLowerCase();
        Set<String> tags = new LinkedHashSet<>();
        TAG_RULES.forEach((tag, keywords) -> {
            if (keywords.stream().anyMatch(lower::contains)) {
                tags.add(tag);
            }
        });
        return List.copyOf(tags);
    }
}
