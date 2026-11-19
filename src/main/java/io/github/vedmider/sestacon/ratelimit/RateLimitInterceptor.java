package io.github.vedmider.sestacon.ratelimit;

import io.github.vedmider.sestacon.http.ClientIp;
import io.github.vedmider.sestacon.logging.SecurityAudit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final boolean trustForwardedHeader;
    private final java.util.List<String> trustedProxies;

    public RateLimitInterceptor(RateLimiter rateLimiter, boolean trustForwardedHeader) {
        this(rateLimiter, trustForwardedHeader, java.util.List.of());
    }

    public RateLimitInterceptor(RateLimiter rateLimiter, boolean trustForwardedHeader,
                                java.util.List<String> trustedProxies) {
        this.rateLimiter = rateLimiter;
        this.trustForwardedHeader = trustForwardedHeader;
        this.trustedProxies = trustedProxies == null ? java.util.List.of() : java.util.List.copyOf(trustedProxies);
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
        boolean viaTrustedProxy = trustForwardedHeader
                && ClientIp.isTrustedPeer(request.getRemoteAddr(), trustedProxies);
        return viaTrustedProxy ? ClientIp.of(request) : request.getRemoteAddr();
    }
}
