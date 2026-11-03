package com.example.rag;

import com.example.rag.domain.port.out.AnswerCache;
import com.example.rag.domain.port.out.ChatProvider;
import com.example.rag.domain.port.out.DocumentStore;
import com.example.rag.domain.port.out.EmbeddingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real local embedding path through {@link EmbeddingProvider} (backed by
 * the ONNX model). Other outbound ports are mocked so no DB/LLM/Redis is required.
 */
@SpringBootTest(properties =
        "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration," +
        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
        "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration," +
        "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration")
class EmbeddingModelTest {

    @Autowired
    private EmbeddingProvider embeddingProvider;

    @MockBean
    private ChatProvider chatProvider;
    @MockBean
    private DocumentStore documentStore;
    @MockBean
    private AnswerCache answerCache;

    @Test
    void producesVectorsOfConfiguredDimension() {
        float[] vector = embeddingProvider.embed("Hello vector space");
        assertThat(vector).hasSize(384);
        assertThat(embeddingProvider.dimensions()).isEqualTo(384);
    }

    @Test
    void similarSentencesAreCloserThanDissimilarOnes() {
        float[] base = embeddingProvider.embed("The cat sat on the mat.");
        float[] similar = embeddingProvider.embed("A cat was sitting on a rug.");
        float[] dissimilar = embeddingProvider.embed("Quarterly revenue exceeded analyst expectations.");

        assertThat(cosine(base, similar)).isGreaterThan(cosine(base, dissimilar));
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
