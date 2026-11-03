package com.example.rag.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditTest {

    private Logger securityLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attach() {
        securityLogger = (Logger) LoggerFactory.getLogger("SECURITY");
        appender = new ListAppender<>();
        appender.start();
        securityLogger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        securityLogger.detachAppender(appender);
    }

    private ILoggingEvent only() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    @Test
    void injectionBlockIsLoggedAsSecurityWarning() {
        SecurityAudit.promptInjectionBlocked();
        ILoggingEvent e = only();
        assertThat(e.getLevel()).isEqualTo(Level.WARN);
        assertThat(e.getMarkerList()).extracting(org.slf4j.Marker::getName).contains("SECURITY");
        assertThat(e.getFormattedMessage()).contains("prompt_injection_blocked");
    }

    @Test
    void rateLimitIsLoggedWithClientAndPath() {
        SecurityAudit.rateLimited("1.2.3.4", "/api/chat");
        assertThat(only().getFormattedMessage())
                .contains("rate_limited").contains("1.2.3.4").contains("/api/chat");
    }

    @Test
    void piiRedactionLogsTypesButNoValues() {
        SecurityAudit.piiRedacted(Map.of("EMAIL", 2));
        String msg = only().getFormattedMessage();
        assertThat(msg).contains("pii_redacted").contains("EMAIL");
    }

    @Test
    void ingestRejectionIsLogged() {
        SecurityAudit.ingestRejected("evil.exe", "unsupported_type:application/zip");
        assertThat(only().getFormattedMessage())
                .contains("ingest_rejected").contains("evil.exe").contains("unsupported_type");
    }

    @Test
    void outputWithheldIsLogged() {
        SecurityAudit.outputWithheld();
        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFormattedMessage()).contains("output_withheld");
    }
}
