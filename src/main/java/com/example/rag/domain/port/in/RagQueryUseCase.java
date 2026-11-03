package com.example.rag.domain.port.in;

import com.example.rag.domain.model.Answer;

import java.util.List;

/** Inbound port: answer a question using retrieval-augmented generation. */
public interface RagQueryUseCase {

    /** Answers with no caller roles — retrieves only public chunks under RBAC-aware stores. */
    default Answer answer(String question) {
        return answer(question, List.of());
    }

    /** Answers using only chunks visible to the given caller {@code roles}. */
    Answer answer(String question, List<String> roles);
}
