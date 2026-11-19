package io.github.vedmider.sestacon.adapter.out.cache;

import io.github.vedmider.sestacon.config.CacheProperties;
import io.github.vedmider.sestacon.domain.model.CachedAnswer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisAnswerCacheTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final CacheProperties props = new CacheProperties();

    private RedisAnswerCache cache() {
        return new RedisAnswerCache(redis, new ObjectMapper(), props);
    }

    @Test
    void disabledCacheIsANoOp() {
        props.setEnabled(false);
        RedisAnswerCache cache = cache();

        assertThat(cache.findSimilar(new float[]{1f}, java.util.List.of())).isEmpty();
        cache.store(new CachedAnswer("q", new float[]{1f}, "a", List.of("s")), java.util.List.of());

        verifyNoInteractions(redis);
    }

    @Test
    void lookupDegradesToMissWhenRedisFails() {
        when(redis.opsForList()).thenThrow(new RuntimeException("redis down"));
        assertThat(cache().findSimilar(new float[]{1f, 2f}, java.util.List.of())).isEmpty();
    }

    @Test
    void storeSwallowsRedisFailures() {
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        // Must not throw — caching is best-effort.
        cache().store(new CachedAnswer("q", new float[]{1f}, "a", List.of("s")), java.util.List.of());
    }

    @Test
    void roleKeysAreCaseSensitiveCollisionFreeAndOrderInsensitive() {
        assertThat(RedisAnswerCache.roleKey(List.of("FINANCE"))).isNotEqualTo(RedisAnswerCache.roleKey(List.of("finance")));
        assertThat(RedisAnswerCache.roleKey(List.of("team.a"))).isNotEqualTo(RedisAnswerCache.roleKey(List.of("team_a")));
        assertThat(RedisAnswerCache.roleKey(List.of("a,b"))).isNotEqualTo(RedisAnswerCache.roleKey(List.of("a", "b")));
        assertThat(RedisAnswerCache.roleKey(List.of("b", "a"))).isEqualTo(RedisAnswerCache.roleKey(List.of("a", "b", "a")));
    }

    @Test
    void invalidateAllMovesLookupsToANewGeneration() {
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<String, String> values =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ListOperations<String, String> lists =
                mock(org.springframework.data.redis.core.ListOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForList()).thenReturn(lists);
        when(values.get("ragcache:gen")).thenReturn("0", "1");

        RedisAnswerCache cache = cache();
        cache.findSimilar(new float[]{1f}, List.of());
        cache.invalidateAll();
        cache.findSimilar(new float[]{1f}, List.of());

        org.mockito.Mockito.verify(values).increment("ragcache:gen");
        org.mockito.Mockito.verify(lists).range("ragcache:index:default:0:public", 0, -1);
        org.mockito.Mockito.verify(lists).range("ragcache:index:default:1:public", 0, -1);
    }

    @Test
    void answersCachedUnderOneRetrievalConfigurationAreNotServedUnderAnother() {
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<String, String> values =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ListOperations<String, String> lists =
                mock(org.springframework.data.redis.core.ListOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForList()).thenReturn(lists);

        new RedisAnswerCache(redis, new ObjectMapper(), props, "cfgA").findSimilar(new float[]{1f}, List.of());
        new RedisAnswerCache(redis, new ObjectMapper(), props, "cfgB").findSimilar(new float[]{1f}, List.of());

        org.mockito.Mockito.verify(lists).range("ragcache:index:cfgA:0:public", 0, -1);
        org.mockito.Mockito.verify(lists).range("ragcache:index:cfgB:0:public", 0, -1);
    }

    @Test
    void theConfigurationFingerprintChangesWithAnyAnswerShapingSetting() {
        var env = new org.springframework.mock.env.MockEnvironment()
                .withProperty("app.retrieval.min-score", "0.3").withProperty("app.rag.top-k", "4");
        String before = RedisAnswerCache.fingerprint(env);
        env.setProperty("app.retrieval.min-score", "0.4");

        assertThat(RedisAnswerCache.fingerprint(env)).isNotEqualTo(before).hasSize(12);
    }
}
