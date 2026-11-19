package io.github.vedmider.sestacon.domain.port.out;

import io.github.vedmider.sestacon.domain.model.CachedAnswer;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for a semantic answer cache keyed by query embedding, scoped by the
 * caller's roles. Entries are isolated per role-set so a cached answer produced for one
 * set of roles is never served to a caller with a different set (no cross-role leak).
 */
public interface AnswerCache {

    Optional<CachedAnswer> findSimilar(float[] queryEmbedding, List<String> roles);

    void store(CachedAnswer entry, List<String> roles);

    /** Drop every cached answer; called on any corpus change (ingest, re-ingest, delete). */
    default void invalidateAll() {
    }

    /** No-op cache for components constructed without one. */
    final class None implements AnswerCache {
        @Override
        public Optional<CachedAnswer> findSimilar(float[] queryEmbedding, List<String> roles) {
            return Optional.empty();
        }

        @Override
        public void store(CachedAnswer entry, List<String> roles) {
        }
    }
}
