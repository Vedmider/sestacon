package com.example.rag.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextualChunkerTest {

    private static DocumentChunk chunk(String doc, String section, String content) {
        return new DocumentChunk(doc, section, content, Map.of(), List.of("public"));
    }

    @Test
    void prependsDocAndSectionHeader() {
        DocumentChunk out = ContextualChunker.one(chunk("guide.md", "Limits", "body"));
        assertThat(out.content()).isEqualTo("[guide.md > Limits]\nbody");
    }

    @Test
    void headerUsesDocOnlyWhenSectionBlank() {
        DocumentChunk out = ContextualChunker.one(chunk("guide.md", "", "body"));
        assertThat(out.content()).isEqualTo("[guide.md]\nbody");
    }

    @Test
    void preservesMetadataAndAcl() {
        DocumentChunk in = new DocumentChunk("d", "s", "body", Map.of("k", "v"), List.of("eng"));
        DocumentChunk out = ContextualChunker.one(in);
        assertThat(out.metadata()).containsEntry("k", "v");
        assertThat(out.aclTags()).containsExactly("eng");
    }

    @Test
    void isIdempotentWhenHeaderAlreadyPresent() {
        DocumentChunk once = ContextualChunker.one(chunk("d", "s", "body"));
        DocumentChunk twice = ContextualChunker.one(once);
        assertThat(twice.content()).isEqualTo(once.content());
    }

    @Test
    void noHeaderWhenDocAndSectionBlankLeavesContentUnchanged() {
        DocumentChunk out = ContextualChunker.one(chunk("", "", "body"));
        assertThat(out.content()).isEqualTo("body");
    }
}
