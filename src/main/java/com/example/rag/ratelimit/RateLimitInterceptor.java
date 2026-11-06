package com.example.rag.ratelimit;

import com.example.rag.http.ClientIp;
import com.example.rag.logging.SecurityAudit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final boolean trustForwardedHeader;

    public RateLimitInterceptor(RateLimiter rateLimiter, boolean trustForwardedHeader) {
        this.rateLimiter = rateLimiter;
        this.trustForwardedHeader = trustForwardedHeader;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String key = clientKey(request);
        if (rateLimiter.tryAcquire(key)) {
            return true;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(rateLimiter.retryAfterSeconds()));
        SecurityAudit.rateLimited(key, request.getRequestURI());
        return false;
    }

    // XFF is spoofable, so trust it only behind a known proxy (opt-in); otherwise use the
    // socket peer address, which a client cannot forge.
    private String clientKey(HttpServletRequest request) {
        return trustForwardedHeader ? ClientIp.of(request) : request.getRemoteAddr();
    }
}
