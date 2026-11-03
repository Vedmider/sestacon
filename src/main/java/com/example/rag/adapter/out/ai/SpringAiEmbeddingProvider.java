package com.example.rag.adapter.out.ai;

import com.example.rag.domain.port.out.EmbeddingProvider;
import org.springframework.ai.embedding.EmbeddingModel;

/** {@link EmbeddingProvider} backed by Spring AI's local {@link EmbeddingModel} (ONNX). */
public class SpringAiEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingModel embeddingModel;

    public SpringAiEmbeddingProvider(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    @Override
    public int dimensions() {
        return embeddingModel.dimensions();
    }
}
