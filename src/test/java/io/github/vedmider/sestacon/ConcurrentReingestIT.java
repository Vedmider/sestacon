package io.github.vedmider.sestacon;

import io.github.vedmider.sestacon.domain.port.in.DocumentIngestionUseCase;
import io.github.vedmider.sestacon.domain.port.out.ChatProvider;
import io.github.vedmider.sestacon.support.DeterministicEmbeddingModel;
import io.github.vedmider.sestacon.support.FakeChatProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "app.retrieval.mode=hybrid",
        "app.ingest.contextual=false",
        "spring.ai.vectorstore.pgvector.dimensions=384",
        "spring.autoconfigure.exclude=org.springframework.ai.model.transformers.autoconfigure.TransformersEmbeddingModelAutoConfiguration"
})
class ConcurrentReingestIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @TestConfiguration
    static class TestBeans {
        @Bean
        EmbeddingModel embeddingModel() {
            return new DeterministicEmbeddingModel(384);
        }

        @Bean
        ChatProvider chatProvider() {
            return new FakeChatProvider("ok");
        }
    }

    @Autowired
    private DocumentIngestionUseCase ingest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentReingestsOfOneDocumentLeaveExactlyOneVersionStored() throws Exception {
        jdbc.execute("TRUNCATE TABLE rag_chunks");
        int writers = 12;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> done = new ArrayList<>();
        for (int i = 0; i < writers; i++) {
            byte[] body = ("The race value is " + i + ".").getBytes();
            done.add(pool.submit(() -> {
                start.await();
                return ingest.ingestWithVersion("race.txt", body, List.of(), null);
            }));
        }
        start.countDown();
        for (Future<?> f : done) {
            f.get();
        }
        pool.shutdown();

        List<String> stored = jdbc.queryForList("SELECT content FROM rag_chunks WHERE document_id = 'race.txt'",
                String.class);
        assertThat(stored).as("chunks of interleaved versions must not be merged").hasSize(1);
    }
}
