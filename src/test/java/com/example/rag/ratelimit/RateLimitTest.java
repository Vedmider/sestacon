package com.example.rag.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitTest {

    @RestController
    static class DummyController {
        @PostMapping("/api/chat")
        String chat() {
            return "ok";
        }
    }

    @Test
    void tokenBucketAllowsCapacityThenBlocks() {
        TokenBucket bucket = new TokenBucket(2, 0);
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void tokenBucketRefillsOverTime() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(1, 50); // 50 tokens/sec -> ~20ms per token
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
        Thread.sleep(60);
        assertThat(bucket.tryConsume()).isTrue();
    }

    @Test
    void limiterIsolatesBucketsPerKey() {
        RateLimitProperties props = new RateLimitProperties();
        props.setRequestsPerMinute(1);
        RateLimiter limiter = new RateLimiter(props);
        assertThat(limiter.tryAcquire("clientA")).isTrue();
        assertThat(limiter.tryAcquire("clientA")).isFalse();
        assertThat(limiter.tryAcquire("clientB")).isTrue(); // independent bucket
    }

    @Test
    void retryAfterSecondsScalesWithRate() {
        RateLimitProperties p = new RateLimitProperties();
        RateLimiter l = new RateLimiter(p);
        p.setRequestsPerMinute(60);
        assertThat(l.retryAfterSeconds()).isEqualTo(1);
        p.setRequestsPerMinute(30);
        assertThat(l.retryAfterSeconds()).isEqualTo(2);
        p.setRequestsPerMinute(120);
        assertThat(l.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void returns429WithRetryAfterWhenLimitExceeded() throws Exception {
        RateLimitProperties props = new RateLimitProperties();
        props.setRequestsPerMinute(2);
        RateLimiter limiter = new RateLimiter(props);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .addInterceptors(new RateLimitInterceptor(limiter, false))
                .build();

        mockMvc.perform(post("/api/chat")).andExpect(status().isOk());
        mockMvc.perform(post("/api/chat")).andExpect(status().isOk());
        mockMvc.perform(post("/api/chat"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void ignoresForwardedHeaderByDefault() throws Exception {
        RateLimitProperties props = new RateLimitProperties();
        props.setRequestsPerMinute(1);
        RateLimitInterceptor interceptor = new RateLimitInterceptor(new RateLimiter(props), false);

        org.springframework.mock.web.MockHttpServletRequest r1 = new org.springframework.mock.web.MockHttpServletRequest();
        r1.setRemoteAddr("10.0.0.1");
        r1.addHeader("X-Forwarded-For", "9.9.9.9");
        org.springframework.mock.web.MockHttpServletRequest r2 = new org.springframework.mock.web.MockHttpServletRequest();
        r2.setRemoteAddr("10.0.0.1");
        r2.addHeader("X-Forwarded-For", "8.8.8.8");

        assertThat(interceptor.preHandle(r1, new org.springframework.mock.web.MockHttpServletResponse(), null)).isTrue();
        // Same socket peer -> same bucket, different XFF ignored.
        assertThat(interceptor.preHandle(r2, new org.springframework.mock.web.MockHttpServletResponse(), null)).isFalse();
    }

    @Test
    void usesForwardedHeaderWhenTrusted() throws Exception {
        RateLimitProperties props = new RateLimitProperties();
        props.setRequestsPerMinute(1);
        RateLimitInterceptor interceptor = new RateLimitInterceptor(new RateLimiter(props), true);

        org.springframework.mock.web.MockHttpServletRequest r1 = new org.springframework.mock.web.MockHttpServletRequest();
        r1.setRemoteAddr("10.0.0.1");
        r1.addHeader("X-Forwarded-For", "9.9.9.9");
        org.springframework.mock.web.MockHttpServletRequest r2 = new org.springframework.mock.web.MockHttpServletRequest();
        r2.setRemoteAddr("10.0.0.1");
        r2.addHeader("X-Forwarded-For", "8.8.8.8");

        assertThat(interceptor.preHandle(r1, new org.springframework.mock.web.MockHttpServletResponse(), null)).isTrue();
        // Different trusted XFF -> different bucket.
        assertThat(interceptor.preHandle(r2, new org.springframework.mock.web.MockHttpServletResponse(), null)).isTrue();
    }

    @Test
    void disabledLimiterAlwaysAllows() {
        RateLimitProperties props = new RateLimitProperties();
        props.setEnabled(false);
        props.setRequestsPerMinute(1);
        RateLimiter limiter = new RateLimiter(props);

        assertThat(limiter.tryAcquire("x")).isTrue();
        assertThat(limiter.tryAcquire("x")).isTrue();
    }
}
