package io.github.vedmider.sestacon.domain.model;

import java.util.List;

/** The answer returned to a caller. Serialised as {@code {answer, sources, cached}}. */
public record Answer(String answer, List<String> sources, boolean cached) {
}
