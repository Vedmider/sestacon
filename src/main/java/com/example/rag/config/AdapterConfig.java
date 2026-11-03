package com.example.rag.config;

import com.example.rag.adapter.out.ai.OpenAiEmbeddingProvider;
import com.example.rag.adapter.out.ai.SpringAiChatProvider;
import com.example.rag.adapter.out.ai.SpringAiEmbeddingProvider;
import com.example.rag.adapter.out.cache.RedisAnswerCache;
import com.example.rag.adapter.out.parser.TikaDocumentParser;
import com.example.rag.adapter.out.store.HybridPgDocumentStore;
import com.example.rag.adapter.out.store.PgVectorDocumentStore;
import com.example.rag.domain.port.out.AnswerCache;
import com.example.rag.domain.port.out.ChatProvider;
import com.example.rag.domain.port.out.DocumentParser;
import com.example.rag.domain.port.out.DocumentStore;
import com.example.rag.domain.port.out.EmbeddingProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Wires the default outbound adapters for each domain port. Every bean is
 * {@code @ConditionalOnMissingBean} on its port type, so providing your own
 * implementation of any port (a new AI provider, a different store, etc.) transparently
 * replaces the default — the business logic is untouched. Declared as an
 * auto-configuration so it is evaluated after application/test beans.
 */
@AutoConfiguration
public class AdapterConfig {

    @Bean
    @ConditionalOnMissingBean(ChatProvider.class)
    ChatProvider chatProvider(ChatClient.Builder chatClientBuilder) {
        return new SpringAiChatProvider(chatClientBuilder);
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(name = "app.embedding.provider", havingValue = "local", matchIfMissing = true)
    EmbeddingProvider localEmbeddingProvider(EmbeddingModel embeddingModel) {
        return new SpringAiEmbeddingProvider(embeddingModel);
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(name = "app.embedding.provider", havingValue = "openai")
    EmbeddingProvider openAiEmbeddingProvider(EmbeddingProperties props) {
        RestClient.Builder builder = RestClient.builder().baseUrl(props.getOpenai().getBaseUrl());
        if (StringUtils.hasText(props.getOpenai().getApiKey())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getOpenai().getApiKey());
        }
        return new OpenAiEmbeddingProvider(builder.build(), props.getOpenai().getModel(),
                props.getDimensions());
    }

    @Bean
    @ConditionalOnMissingBean(DocumentStore.class)
    @ConditionalOnProperty(name = "app.retrieval.mode", havingValue = "vector", matchIfMissing = true)
    DocumentStore documentStore(VectorStore vectorStore) {
        return new PgVectorDocumentStore(vectorStore);
    }

    @Bean
    @ConditionalOnMissingBean(DocumentStore.class)
    @ConditionalOnProperty(name = "app.retrieval.mode", havingValue = "hybrid")
    DocumentStore hybridDocumentStore(JdbcTemplate jdbcTemplate, EmbeddingProvider embeddingProvider,
                                      ObjectMapper objectMapper, RetrievalProperties retrievalProps) {
        return new HybridPgDocumentStore(jdbcTemplate, embeddingProvider, objectMapper,
                retrievalProps.getRrfK(), retrievalProps.getTextSearchConfig());
    }

    @Bean
    @ConditionalOnMissingBean(AnswerCache.class)
    AnswerCache answerCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                            CacheProperties properties) {
        return new RedisAnswerCache(redis, objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean(DocumentParser.class)
    DocumentParser documentParser() {
        return new TikaDocumentParser();
    }
}
