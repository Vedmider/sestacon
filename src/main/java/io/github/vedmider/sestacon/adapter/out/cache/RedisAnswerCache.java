package io.github.vedmider.sestacon.adapter.out.cache;

import io.github.vedmider.sestacon.config.CacheProperties;
import io.github.vedmider.sestacon.domain.model.CachedAnswer;
import io.github.vedmider.sestacon.domain.port.out.AnswerCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * {@link AnswerCache} backed by Redis. Entries are stored as JSON keyed by a random id;
 * a per-role-set index list bounds the cache (FIFO eviction). Lookup is a linear cosine
 * scan over the index for the caller's role-set only, so a cached answer is never served
 * to a caller with a different role-set. All Redis access is best-effort — failures
 * degrade to a cache miss/no-op.
 */
public class RedisAnswerCache implements AnswerCache {

    private static final Logger log = LoggerFactory.getLogger(RedisAnswerCache.class);
    private static final String INDEX_PREFIX = "ragcache:index:";
    private static final String ENTRY_PREFIX = "ragcache:entry:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CacheProperties properties;

    public RedisAnswerCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                            CacheProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<CachedAnswer> findSimilar(float[] queryEmbedding, List<String> roles) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        String indexKey = INDEX_PREFIX + roleKey(roles);
        try {
            List<String> ids = redis.opsForList().range(indexKey, 0, -1);
            if (ids == null || ids.isEmpty()) {
                return Optional.empty();
            }
            CachedAnswer best = null;
            double bestScore = properties.getSimilarityThreshold();
            for (String id : ids) {
                String json = redis.opsForValue().get(ENTRY_PREFIX + id);
                if (json == null) {
                    continue;
                }
                CachedAnswer entry = objectMapper.readValue(json, CachedAnswer.class);
                double score = cosine(queryEmbedding, entry.embedding());
                if (score >= bestScore) {
                    bestScore = score;
                    best = entry;
                }
            }
            return Optional.ofNullable(best);
        }
        catch (Exception e) {
            log.warn("Semantic cache lookup failed, continuing without cache: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void store(CachedAnswer entry, List<String> roles) {
        if (!properties.isEnabled()) {
            return;
        }
        String indexKey = INDEX_PREFIX + roleKey(roles);
        try {
            String id = UUID.randomUUID().toString();
            redis.opsForValue().set(ENTRY_PREFIX + id, objectMapper.writeValueAsString(entry),
                    Duration.ofSeconds(properties.getTtlSeconds()));
            redis.opsForList().leftPush(indexKey, id);
            evictOverflow(indexKey);
        }
        catch (Exception e) {
            log.warn("Semantic cache store failed, continuing without cache: {}", e.getMessage());
        }
    }

    /** Stable, injection-safe key for a role-set: normalised (trim, lowercase, sorted, unique). */
    private static String roleKey(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "public";
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String role : roles) {
            if (role != null && !role.isBlank()) {
                normalized.add(role.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_"));
            }
        }
        return normalized.isEmpty() ? "public" : String.join(",", new ArrayList<>(normalized));
    }

    private void evictOverflow(String indexKey) {
        Long size = redis.opsForList().size(indexKey);
        if (size == null) {
            return;
        }
        while (size > properties.getMaxEntries()) {
            String evicted = redis.opsForList().rightPop(indexKey);
            if (evicted == null) {
                break;
            }
            redis.delete(ENTRY_PREFIX + evicted);
            size--;
        }
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return -1.0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return -1.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
