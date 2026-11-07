package io.github.vedmider.sestacon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Selects and configures the embedding backend. {@code provider=local} uses the bundled
 * ONNX model (default); {@code provider=openai} calls an external OpenAI-compatible
 * embeddings API. The vector dimension must match whichever model is used.
 */
@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingProperties {

    /** local | openai */
    private String provider = "local";

    /** Embedding vector dimension — must equal the model's output and the pgvector column. */
    private int dimensions = 384;

    private final OpenAi openai = new OpenAi();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public static class OpenAi {
        private String baseUrl = "https://api.openai.com";
        private String apiKey = "";
        private String model = "text-embedding-3-small";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
