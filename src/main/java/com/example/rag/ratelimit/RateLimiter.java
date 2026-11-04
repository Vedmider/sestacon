package com.example.rag.ratelimit;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RateLimiter {

    private final RateLimitProperties properties;
    private final Map<String, TokenBucket> buckets;

    public RateLimiter(RateLimitProperties properties) {
        this.properties = properties;
        int max = (int) Math.min(Integer.MAX_VALUE, Math.max(1, properties.getMaxTrackedClients()));
        // Access-ordered LRU bounded by max entries — evicts the least-recently-used bucket
        // so an attacker rotating keys (e.g. spoofed X-Forwarded-For) cannot grow the heap.
        this.buckets = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TokenBucket> eldest) {
                return size() > max;
            }
        });
    }

    public boolean tryAcquire(String key) {
        if (!properties.isEnabled()) {
            return true;
        }
        int rpm = properties.getRequestsPerMinute();
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(rpm, rpm / 60.0));
        return bucket.tryConsume();
    }

    public int retryAfterSeconds() {
        int rpm = Math.max(1, properties.getRequestsPerMinute());
        return Math.max(1, (int) Math.ceil(60.0 / rpm));
    }
}
