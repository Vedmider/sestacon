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
    void imageWithoutOcrBackendYieldsAFilenameSurrogateChunk() {
        // 1x1 transparent PNG: a real image Tika detects but cannot extract text from here (no tesseract).
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
        List<DocumentChunk> chunks = parser.parse("diagram.png", png);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo("[diagram.png]");
        assertThat(chunks.get(0).metadata()).containsEntry("source", "diagram.png")
                .containsEntry("extracted", false);
    }

    @Test
    void blankExtractionFallsBackToASurrogateRatherThanZeroChunks() {
        List<DocumentChunk> chunks = parser.parse("empty.txt", "   \n  ".getBytes(StandardCharsets.UTF_8));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo("[empty.txt]");
        assertThat(chunks.get(0).metadata()).containsEntry("extracted", false);
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
