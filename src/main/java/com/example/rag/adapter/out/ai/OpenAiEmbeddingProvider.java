package com.example.rag.adapter.out.ai;

import com.example.rag.domain.port.out.EmbeddingProvider;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * {@link EmbeddingProvider} that calls an external OpenAI-compatible embeddings API
 * ({@code POST {baseUrl}/v1/embeddings}). Selected via {@code app.embedding.provider=openai};
 * useful for hosted multilingual or higher-quality embedders.
 */
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private final RestClient restClient;
    private final String model;
    private final int dimensions;

    public OpenAiEmbeddingProvider(RestClient restClient, String model, int dimensions) {
        this.restClient = restClient;
        this.model = model;
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        EmbeddingResponse response = restClient.post()
                .uri("/v1/embeddings")
                .body(new EmbeddingRequest(model, List.of(text)))
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("Empty embeddings response from provider");
        }
        return response.data().get(0).embedding();
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    public record EmbeddingRequest(String model, List<String> input) {
    }

    public record EmbeddingResponse(List<Data> data) {
        public record Data(float[] embedding) {
        }
    }
}
