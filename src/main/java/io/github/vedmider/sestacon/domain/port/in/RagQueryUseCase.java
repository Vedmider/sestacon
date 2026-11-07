package io.github.vedmider.sestacon.domain.port.in;

import io.github.vedmider.sestacon.domain.model.Answer;

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
