package io.github.vedmider.sestacon.adapter.in.web;

import java.util.Arrays;
import java.util.List;

import org.springframework.util.StringUtils;

/** Parses the comma-separated {@code X-Roles} header into caller roles for RBAC-scoped retrieval. */
final class RoleHeader {

    private RoleHeader() {
    }

    static List<String> parse(String header) {
        if (!StringUtils.hasText(header)) {
            return List.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
