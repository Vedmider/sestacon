package io.github.vedmider.sestacon.domain.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentDiffTest {

    private static Map<String, String> sections(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void noPriorVersionIsNew() {
        DocumentDiff d = DocumentDiff.compare("doc", Map.of(), sections("c0", "h0", "c1", "h1"));
        assertThat(d.status()).isEqualTo(DocumentDiff.Status.NEW);
        assertThat(d.changedSections()).containsExactly("c0", "c1");
    }

    @Test
    void identicalContentIsUnchanged() {
        Map<String, String> prev = sections("c0", "h0", "c1", "h1");
        DocumentDiff d = DocumentDiff.compare("doc", prev, sections("c0", "h0", "c1", "h1"));
        assertThat(d.status()).isEqualTo(DocumentDiff.Status.UNCHANGED);
        assertThat(d.changedSections()).isEmpty();
    }

    @Test
    void oneChangedSectionWithSurvivorsIsPartial() {
        Map<String, String> prev = sections("c0", "h0", "c1", "h1");
        DocumentDiff d = DocumentDiff.compare("doc", prev, sections("c0", "h0", "c1", "CHANGED"));
        assertThat(d.status()).isEqualTo(DocumentDiff.Status.PARTIAL);
        assertThat(d.changedSections()).containsExactly("c1");
    }

    @Test
    void everySectionChangedIsFull() {
        Map<String, String> prev = sections("c0", "h0", "c1", "h1");
        DocumentDiff d = DocumentDiff.compare("doc", prev, sections("c0", "X", "c1", "Y"));
        assertThat(d.status()).isEqualTo(DocumentDiff.Status.FULL);
        assertThat(d.changedSections()).containsExactly("c0", "c1");
    }

    @Test
    void removedAndAddedSectionsAreBothReported() {
        Map<String, String> prev = sections("c0", "h0", "c1", "h1");
        DocumentDiff d = DocumentDiff.compare("doc", prev, sections("c0", "h0", "c2", "h2"));
        assertThat(d.status()).isEqualTo(DocumentDiff.Status.PARTIAL);
        assertThat(d.changedSections()).containsExactlyInAnyOrder("c2", "c1");
    }
}
