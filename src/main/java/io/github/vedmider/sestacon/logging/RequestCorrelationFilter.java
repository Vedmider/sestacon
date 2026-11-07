package io.github.vedmider.sestacon.logging;

import io.github.vedmider.sestacon.http.ClientIp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds request correlation to every log line via MDC (requestId, clientIp, method,
 * path) and emits one access log per request with status and duration. The requestId
 * is echoed back in the X-Request-Id response header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);
    private static final String REQUEST_ID = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put("requestId", requestId);
        MDC.put("clientIp", ClientIp.of(request));
        MDC.put("method", request.getMethod());
        MDC.put("path", request.getRequestURI());
        response.setHeader(REQUEST_ID, requestId);

        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        }
        finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("event=http_request status={} durationMs={}", response.getStatus(), ms);
            MDC.clear();
        }
    }
}
