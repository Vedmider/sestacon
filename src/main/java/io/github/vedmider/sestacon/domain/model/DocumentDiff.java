package io.github.vedmider.sestacon.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The change a re-ingest makes to a document, computed from per-chunk content hashes — the signal
 * the app hands the AI so it decides whether/what to re-ingest (the app never decides).
 *
 * @param changedSections section paths that were added, modified or removed (empty when UNCHANGED)
 */
public record DocumentDiff(String documentId, Status status, List<String> changedSections) {

    public enum Status {
        /** No prior version existed. */
        NEW,
        /** Same content as the stored version — nothing to re-ingest. */
        UNCHANGED,
        /** Prior version existed but no section survived — a full replacement. */
        FULL,
        /** Some sections changed/added/removed while others are unchanged. */
        PARTIAL
    }

    /**
     * Classify by comparing stored section→hash against the incoming section→hash. Order of
     * {@code incoming} is preserved in {@code changedSections}; removed sections are appended.
     */
    public static DocumentDiff compare(String documentId, Map<String, String> previous,
                                       Map<String, String> incoming) {
        if (previous == null || previous.isEmpty()) {
            return new DocumentDiff(documentId, Status.NEW, List.copyOf(incoming.keySet()));
        }
        List<String> changed = new ArrayList<>();
        int survivors = 0;
        for (Map.Entry<String, String> e : incoming.entrySet()) {
            String prior = previous.get(e.getKey());
            if (prior == null || !prior.equals(e.getValue())) {
                changed.add(e.getKey());
            }
            else {
                survivors++;
            }
        }
        for (String prev : previous.keySet()) {
            if (!incoming.containsKey(prev)) {
                changed.add(prev);
            }
        }
        if (changed.isEmpty()) {
            return new DocumentDiff(documentId, Status.UNCHANGED, List.of());
        }
        Status status = survivors == 0 ? Status.FULL : Status.PARTIAL;
        return new DocumentDiff(documentId, status, List.copyOf(changed));
    }
}
