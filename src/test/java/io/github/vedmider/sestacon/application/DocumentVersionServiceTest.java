package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.model.DocumentDiff;
import io.github.vedmider.sestacon.domain.model.DocumentVersion;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.domain.port.out.DocumentVersionStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentVersionServiceTest {

    private static DocumentVersionStore versionStoreWith(DocumentVersion v) {
        return new DocumentVersionStore() {
            public DocumentDiff diff(String id, Map<String, String> in) { return DocumentDiff.compare(id, Map.of(), in); }
            public void record(String id, String h, long s, Map<String, String> m) { }
            public Optional<DocumentVersion> find(String id) { return Optional.of(v); }
            public int delete(String id) { return 0; }
        };
    }

    private static DocumentStore storeReadable(boolean readable) {
        return new DocumentStore() {
            public void add(List<DocumentChunk> chunks) { }
            public List<RetrievedChunk> search(String q, int k) { return List.of(); }
            public int deleteByDocumentId(String id, List<String> roles) { return 0; }
            public void replace(List<DocumentChunk> chunks, List<String> roles) { }
            public boolean isReadable(String id, List<String> roles) { return readable; }
        };
    }

    private static final DocumentVersion V = new DocumentVersion("d", "h", 1L, 1, Map.of("s", "x"));

    @Test
    void versionIsReturnedWhenTheCallerMayReadTheWholeDocument() {
        var svc = new DocumentVersionService(versionStoreWith(V), storeReadable(true));
        assertThat(svc.version("d", List.of("FINANCE"))).contains(V);
    }

    @Test
    void versionIsWithheldWhenTheDocumentIsNotReadableByTheCaller() {
        var svc = new DocumentVersionService(versionStoreWith(V), storeReadable(false));
        assertThat(svc.version("d", List.of())).isEmpty();
    }
}
