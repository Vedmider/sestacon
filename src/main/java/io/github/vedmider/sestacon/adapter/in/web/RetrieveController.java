package io.github.vedmider.sestacon.adapter.in.web;

import io.github.vedmider.sestacon.config.RagProperties;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.in.RetrieveUseCase;
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

import java.util.List;

/**
 * Inbound HTTP adapter for retrieval-only queries. Returns the top matching chunks and stops
 * before the LLM, so a caller (e.g. an agent) can ground its own reasoning without spending
 * generation tokens. The optional {@code X-Roles} header (comma-separated) scopes retrieval
 * under RBAC-aware stores, as in {@link ChatController}.
 */
@RestController
@RequestMapping("/api/retrieve")
@Tag(name = "Retrieve", description = "Retrieve relevant chunks without calling the LLM")
public class RetrieveController {

    private static final int MAX_TOP_K = 50;

    private final RetrieveUseCase retrieve;
    private final RagProperties ragProperties;

    public RetrieveController(RetrieveUseCase retrieve, RagProperties ragProperties) {
        this.retrieve = retrieve;
        this.ragProperties = ragProperties;
    }

    @PostMapping
    @Operation(summary = "Retrieve chunks",
            description = "Embeds the query, runs similarity/hybrid search and returns the "
                    + "top-K chunks ({text, source, score}) — no LLM is invoked. topK "
                    + "defaults to the server's configured value and is capped at 50. The "
                    + "optional X-Roles header scopes retrieval under RBAC. Returns 400 for a "
                    + "blank or over-long query.")
    public List<RetrievedChunk> retrieve(@RequestBody RetrieveRequest request,
                                         @RequestHeader(value = "X-Roles", required = false)
                                         String rolesHeader) {
        if (request == null || !StringUtils.hasText(request.query())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank");
        }
        if (request.query().length() > ragProperties.getMaxQuestionLength()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "query exceeds max length of " + ragProperties.getMaxQuestionLength()
                            + " characters");
        }
        return retrieve.retrieve(request.query(), effectiveTopK(request.topK()),
                RoleHeader.parse(rolesHeader),
                request.metadata() == null ? java.util.Map.of() : request.metadata());
    }

    private int effectiveTopK(Integer requested) {
        if (requested == null || requested <= 0) {
            return ragProperties.getTopK();
        }
        return Math.min(requested, MAX_TOP_K);
    }

    public record RetrieveRequest(String query, Integer topK, java.util.Map<String, String> metadata) {
    }
}
