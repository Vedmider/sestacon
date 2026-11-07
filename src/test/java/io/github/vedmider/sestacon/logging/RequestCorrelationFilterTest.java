package io.github.vedmider.sestacon.logging;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestCorrelationFilterTest {

    @RestController
    static class DummyController {
        @GetMapping("/ping")
        String ping() {
            return "pong";
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
            .addFilter(new RequestCorrelationFilter())
            .build();

    @Test
    void generatesRequestIdWhenNoneProvided() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id",
                        matchesPattern("[0-9a-fA-F-]{36}")));
    }

    @Test
    void echoesInboundRequestId() throws Exception {
        mockMvc.perform(get("/ping").header("X-Request-Id", "trace-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "trace-123"));
    }
}
