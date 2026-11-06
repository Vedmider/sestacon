package com.example.rag.domain.port.out;

import com.example.rag.domain.model.RetrievedChunk;

import java.util.List;

/**
 * Second-pass precision filter over first-pass recall. Given the query and a wide candidate list,
 * returns the best {@code topK} re-ordered by relevance. The default adapter is a lexical heuristic;
 * a cross-encoder model can replace it without touching callers.
 */
public interface Reranker {

    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK);
}
