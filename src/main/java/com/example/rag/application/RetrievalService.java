package com.example.rag.application;

import com.example.rag.domain.model.RetrievedChunk;
import com.example.rag.domain.port.in.RetrieveUseCase;
import com.example.rag.domain.port.out.DocumentStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Retrieval-only use case: delegates straight to the {@link DocumentStore} (which embeds the
 * query and applies RBAC/hybrid ranking). No cache, no guardrails, no LLM — just the grounded
 * chunks, so an external agent can reason over them cheaply.
 */
@Service
public class RetrievalService implements RetrieveUseCase {

    private final DocumentStore documentStore;

    public RetrievalService(DocumentStore documentStore) {
        this.documentStore = documentStore;
    }

    @Override
    public List<RetrievedChunk> retrieve(String query, int topK, List<String> roles) {
        return documentStore.search(query, topK, roles);
    }
}
