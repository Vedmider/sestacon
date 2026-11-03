package com.example.rag.ratelimit;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiter {

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    public boolean tryAcquire(String key) {
        if (!properties.isEnabled()) {
            return true;
        }
        int rpm = properties.getRequestsPerMinute();
        TokenBucket bucket = buckets.computeIfAbsent(key,
                k -> new TokenBucket(rpm, rpm / 60.0));
        return bucket.tryConsume();
    }

    public int retryAfterSeconds() {
        int rpm = Math.max(1, properties.getRequestsPerMinute());
        return Math.max(1, (int) Math.ceil(60.0 / rpm));
    }
}
