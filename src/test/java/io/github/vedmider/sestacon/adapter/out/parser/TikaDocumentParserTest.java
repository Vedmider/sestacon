package io.github.vedmider.sestacon.adapter.out.parser;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
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

    @Test
    void overlapPrependsPreviousChunkTailSoBoundaryFactsSurvive() {
        List<String> out = TikaDocumentParser.withOverlap(
                List.of("alpha beta BOUNDARYFACT", "gamma delta epsilon"), 10);
        assertThat(out).hasSize(2);
        assertThat(out.get(0)).isEqualTo("alpha beta BOUNDARYFACT");
        assertThat(out.get(1)).startsWith("UNDARYFACT\n").endsWith("gamma delta epsilon");
    }

    @Test
    void overlapIsANoOpForZeroOrSingleChunk() {
        assertThat(TikaDocumentParser.withOverlap(List.of("only"), 10)).containsExactly("only");
        assertThat(TikaDocumentParser.withOverlap(List.of("a", "b"), 0)).containsExactly("a", "b");
    }
}
