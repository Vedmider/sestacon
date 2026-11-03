package com.example.rag.adapter.out.parser;

import com.example.rag.domain.model.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TikaDocumentParserTest {

    private final TikaDocumentParser parser = new TikaDocumentParser();

    @Test
    void parsesTextIntoChunksTaggedWithSource() {
        String text = """
                Kubernetes orchestrates containers. Helm packages charts.
                pgvector adds vector similarity search to PostgreSQL.
                """;
        List<DocumentChunk> chunks = parser.parse("notes.md", text.getBytes(StandardCharsets.UTF_8));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(c -> {
            assertThat(c.documentId()).isEqualTo("notes.md");
            assertThat(c.content()).isNotBlank();
            assertThat(c.aclTags()).containsExactly(DocumentChunk.PUBLIC);
            assertThat(c.metadata()).containsEntry("source", "notes.md");
        });
        assertThat(chunks.stream().map(DocumentChunk::content).reduce("", String::concat))
                .contains("pgvector");
    }
}
