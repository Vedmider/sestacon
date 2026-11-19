package io.github.vedmider.sestacon.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/** A chat answer; {@code contexts} are the chunk texts it was grounded on (kept server-side for evaluation). */
public record Answer(String answer, List<String> sources, boolean cached, boolean confident,
                     @JsonIgnore List<String> contexts) {

    public Answer {
        contexts = contexts == null ? List.of() : List.copyOf(contexts);
    }

    public Answer(String answer, List<String> sources, boolean cached, boolean confident) {
        this(answer, sources, cached, confident, List.of());
    }
}
