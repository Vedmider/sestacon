package io.github.vedmider.sestacon.domain.model;

import java.util.List;

/** The answer returned to a caller. Serialised as {@code {answer, sources, cached, confident}}.
 *  {@code confident} is false when no retrieved chunk cleared the relevance floor (a grounded "no match"). */
public record Answer(String answer, List<String> sources, boolean cached, boolean confident) {
}
