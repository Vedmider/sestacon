package io.github.vedmider.sestacon.adapter.in.web;

import io.github.vedmider.sestacon.domain.model.Answer;
import io.github.vedmider.sestacon.domain.port.in.RagQueryUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wire-contract test for POST /api/chat: exact response media type and JSON schema
 * ({@code answer}:string, {@code sources}:array, {@code cached}:boolean) and the error
 * contract. Framework-free alternative to a Cloud Contract stub.
 */
@WebMvcTest(controllers = ChatController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = io.github.vedmider.sestacon.ratelimit.WebConfig.class))
class ChatApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RagQueryUseCase ragQuery;

    @org.springframework.boot.test.context.TestConfiguration
    static class Props {
        @org.springframework.context.annotation.Bean
        io.github.vedmider.sestacon.config.RagProperties ragProperties() {
            return new io.github.vedmider.sestacon.config.RagProperties();
        }
    }

    @Test
    void successResponseMatchesContract() throws Exception {
        when(ragQuery.answer(anyString(), anyList()))
                .thenReturn(new Answer("42", List.of("doc.txt"), false));

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"meaning of life?\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.answer").isString())
                .andExpect(jsonPath("$.sources").isArray())
                .andExpect(jsonPath("$.cached").isBoolean())
                .andExpect(jsonPath("$.cached").value(false));
    }

    @Test
    void blankQuestionViolatesContractWith400() throws Exception {
        // Status contract. (The web slice doesn't run Boot's error dispatch, so the
        // problem-JSON body is a full-container concern, verified by the live E2E.)
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonJsonContentType() throws Exception {
        mockMvc.perform(post("/api/chat").contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
