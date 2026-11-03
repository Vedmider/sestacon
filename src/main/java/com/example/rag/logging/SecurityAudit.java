package com.example.rag.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Map;

/**
 * Central security audit log. Events are emitted under the dedicated "SECURITY" logger
 * and tagged with the SECURITY marker so they can be routed/alerted on separately.
 *
 * Never log sensitive values (question text, PII, secrets) — only outcomes, counts,
 * client identity, and request correlation already present in the MDC.
 */
public final class SecurityAudit {

    public static final Marker SECURITY = MarkerFactory.getMarker("SECURITY");
    private static final Logger log = LoggerFactory.getLogger("SECURITY");

    private SecurityAudit() {
    }

    public static void promptInjectionBlocked() {
        log.warn(SECURITY, "event=prompt_injection_blocked outcome=denied");
    }

    public static void contextInjectionDropped(String source) {
        log.warn(SECURITY, "event=context_injection_dropped source=\"{}\" outcome=chunk_excluded", source);
    }

    public static void piiRedacted(Map<String, Integer> counts) {
        log.info(SECURITY, "event=pii_redacted types={} outcome=sanitized", counts);
    }

    public static void outputWithheld() {
        log.warn(SECURITY, "event=output_withheld outcome=blocked");
    }

    public static void rateLimited(String clientKey, String path) {
        log.warn(SECURITY, "event=rate_limited client=\"{}\" path=\"{}\" outcome=throttled",
                clientKey, path);
    }

    public static void ingestRejected(String filename, String reason) {
        log.warn(SECURITY, "event=ingest_rejected file=\"{}\" reason=\"{}\" outcome=denied",
                filename, reason);
    }

    public static void chunksIngested(String documentId) {
        log.info(SECURITY, "event=chunks_ingested document=\"{}\" outcome=stored", documentId);
    }
}
