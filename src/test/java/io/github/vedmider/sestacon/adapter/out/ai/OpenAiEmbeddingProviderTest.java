package io.github.vedmider.sestacon.adapter.out.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class OpenAiEmbeddingProviderTest {

    @Test
    void postsToEmbeddingsEndpointAndParsesVector() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://embeddings.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://embeddings.example/v1/embeddings"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.model").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.input[0]").value("hello world"))
                .andRespond(withSuccess(
                        "{\"data\":[{\"embedding\":[0.1,0.2,0.3,0.4]}]}",
                        MediaType.APPLICATION_JSON));

        OpenAiEmbeddingProvider provider =
                new OpenAiEmbeddingProvider(builder.build(), "text-embedding-3-small", 4);

        float[] vector = provider.embed("hello world");

        assertThat(vector).containsExactly(0.1f, 0.2f, 0.3f, 0.4f);
        assertThat(provider.dimensions()).isEqualTo(4);
        server.verify();
    }
}
