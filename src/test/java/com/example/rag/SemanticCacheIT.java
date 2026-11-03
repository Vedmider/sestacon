package com.example.rag;

import com.example.rag.support.DeterministicEmbeddingModel;
import com.example.rag.support.FakeChatProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.ai.vectorstore.pgvector.dimensions=384",
        "app.cache.similarity-threshold=0.99",
        "spring.autoconfigure.exclude="
                + "org.springframework.ai.model.transformers.autoconfigure.TransformersEmbeddingModelAutoConfiguration,"
                + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration"
})
class SemanticCacheIT {

    static final String QUESTION = "What is the capital of France?";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @TestConfiguration
    static class StubModels {
        @Bean
        EmbeddingModel embeddingModel() {
            return new DeterministicEmbeddingModel(384);
        }

        @Bean
        FakeChatProvider chatProvider() {
            return new FakeChatProvider("Paris is the capital of France.");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private FakeChatProvider chatProvider;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @org.junit.jupiter.api.BeforeEach
    void isolate() {
        // Cache + chat provider are singletons shared across methods — reset for isolation.
        chatProvider.reset();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void secondIdenticalQueryIsServedFromCache() throws Exception {
        vectorStore.add(List.of(new Document(
                "Paris is the capital of France.", Map.of("source", "geo.txt"))));

        String payload = "{\"question\": \"" + QUESTION + "\"}";

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false));

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.answer").value("Paris is the capital of France."));

        assertThat(chatProvider.callCount()).isEqualTo(1);
    }

    @Test
    void differentQuestionBelowThresholdIsNotACacheHit() throws Exception {
        vectorStore.add(List.of(new Document(
                "Paris is the capital of France.", Map.of("source", "geo.txt"))));

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Tell me about the Louvre museum\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false));

        // A semantically different question must not be served from a prior entry.
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What currency does Japan use\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false));
    }

    @Test
    void cacheIsIsolatedPerRoleSetSoAnswersDoNotCrossRoles() throws Exception {
        vectorStore.add(List.of(new Document(
                "Paris is the capital of France.", Map.of("source", "geo.txt"))));
        String payload = "{\"question\": \"" + QUESTION + "\"}";

        // Caller with role 'engineering': first call misses and caches under that role-set.
        mockMvc.perform(post("/api/chat").header("X-Roles", "engineering")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(jsonPath("$.cached").value(false));
        // Same role-set: served from cache.
        mockMvc.perform(post("/api/chat").header("X-Roles", "engineering")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(jsonPath("$.cached").value(true));
        // Different role-set (public, no header): must NOT get the engineering-scoped entry.
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(jsonPath("$.cached").value(false));

        // One LLM call per distinct role-set — no cross-role reuse.
        assertThat(chatProvider.callCount()).isEqualTo(2);
    }
}
