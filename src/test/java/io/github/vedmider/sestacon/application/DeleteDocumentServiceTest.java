package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.domain.model.CachedAnswer;
import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.model.DocumentDiff;
import io.github.vedmider.sestacon.domain.model.DocumentVersion;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.out.AnswerCache;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.domain.port.out.DocumentVersionStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DeleteDocumentServiceTest {

    @Test
    void deletingADocumentInvalidatesTheAnswerCache() {
        int[] invalidations = {0};
        AnswerCache cache = new AnswerCache() {
            public Optional<CachedAnswer> findSimilar(float[] e, List<String> r) {
                return Optional.empty();
            }
            public void store(CachedAnswer a, List<String> r) {
            }
            public void invalidateAll() {
                invalidations[0]++;
            }
        };
        DocumentStore store = new DocumentStore() {
            public void add(List<DocumentChunk> chunks) { }
            public List<RetrievedChunk> search(String q, int k) { return List.of(); }
            public int deleteByDocumentId(String id, List<String> roles) { return 1; }
            public void replace(List<DocumentChunk> chunks, List<String> roles) { }
        };
        DocumentVersionStore versions = new DocumentVersionStore() {
            public DocumentDiff diff(String id, Map<String, String> in) { return DocumentDiff.compare(id, Map.of(), in); }
            public void record(String id, String h, long s, Map<String, String> m) { }
            public Optional<DocumentVersion> find(String id) { return Optional.empty(); }
            public int delete(String id) { return 0; }
        };

        int removed = new DeleteDocumentService(store, versions, cache).delete("doc.md", List.of());

        assertThat(removed).isEqualTo(1);
        assertThat(invalidations[0]).isEqualTo(1);
    }

    @Test
    void anRbacRefusalReturnsZeroAndDoesNotInvalidateTheCache() {
        // PT-03: a not-permitted delete must be opaque (same 0 as unknown), not a signal.
        int[] invalidations = {0};
        AnswerCache cache = new AnswerCache() {
            public Optional<CachedAnswer> findSimilar(float[] e, List<String> r) {
                return Optional.empty();
            }
            public void store(CachedAnswer a, List<String> r) {
            }
            public void invalidateAll() {
                invalidations[0]++;
            }
        };
        DocumentStore store = new DocumentStore() {
            public void add(List<DocumentChunk> chunks) { }
            public List<RetrievedChunk> search(String q, int k) { return List.of(); }
            public int deleteByDocumentId(String id, List<String> roles) {
                throw new DeleteNotPermittedException(id);
            }
            public void replace(List<DocumentChunk> chunks, List<String> roles) { }
        };
        DocumentVersionStore versions = new DocumentVersionStore() {
            public DocumentDiff diff(String id, Map<String, String> in) { return DocumentDiff.compare(id, Map.of(), in); }
            public void record(String id, String h, long s, Map<String, String> m) { }
            public Optional<DocumentVersion> find(String id) { return Optional.empty(); }
            public int delete(String id) { return 0; }
        };

        int removed = new DeleteDocumentService(store, versions, cache).delete("eng-only", List.of());

        assertThat(removed).isZero();
        assertThat(invalidations[0]).isZero();
    }
}
