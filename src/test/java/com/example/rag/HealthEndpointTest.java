package com.example.rag;

import com.example.rag.domain.port.out.AnswerCache;
import com.example.rag.domain.port.out.ChatProvider;
import com.example.rag.domain.port.out.DocumentStore;
import com.example.rag.domain.port.out.EmbeddingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DB/LLM-free context test. Mocking the outbound ports makes the Spring AI adapters back
 * off (they are @ConditionalOnMissingBean), so no datasource, model, or Redis is needed.
 */
@SpringBootTest(properties =
        "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration," +
        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
        "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration," +
        "org.springframework.ai.model.transformers.autoconfigure.TransformersEmbeddingModelAutoConfiguration," +
        "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration")
@AutoConfigureMockMvc
class HealthEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatProvider chatProvider;
    @MockBean
    private EmbeddingProvider embeddingProvider;
    @MockBean
    private DocumentStore documentStore;
    @MockBean
    private AnswerCache answerCache;

    @Test
    void contextLoads() {
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
