package com.example.rag.adapter.in.web;

import com.example.rag.domain.model.Answer;
import com.example.rag.domain.port.in.RagQueryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Inbound HTTP adapter for RAG queries. Maps HTTP to the {@link RagQueryUseCase}. */
@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "Ask questions answered from ingested documents")
public class ChatController {

    private final RagQueryUseCase ragQuery;
    private final com.example.rag.config.RagProperties ragProperties;

    public ChatController(RagQueryUseCase ragQuery, com.example.rag.config.RagProperties ragProperties) {
        this.ragQuery = ragQuery;
        this.ragProperties = ragProperties;
    }

    @PostMapping
    @Operation(summary = "Ask a question",
            description = "Retrieves relevant chunks, builds a grounded prompt, calls the LLM, "
                    + "and returns the answer with its sources. The optional X-Roles header "
                    + "(comma-separated) scopes retrieval under RBAC-aware stores. Returns 400 "
                    + "for a blank or over-long question or a detected prompt injection.")
    public Answer chat(@RequestBody ChatRequest request,
                       @RequestHeader(value = "X-Roles", required = false) String rolesHeader) {
        if (request == null || !StringUtils.hasText(request.question())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question must not be blank");
        }
        if (request.question().length() > ragProperties.getMaxQuestionLength()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "question exceeds max length of " + ragProperties.getMaxQuestionLength()
                            + " characters");
        }
        return ragQuery.answer(request.question(), RoleHeader.parse(rolesHeader));
    }

    public record ChatRequest(String question) {
    }
}
