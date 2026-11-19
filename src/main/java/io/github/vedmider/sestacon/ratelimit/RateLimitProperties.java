package io.github.vedmider.sestacon.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int requestsPerMinute = 60;
    private boolean trustForwardedHeader = false;
    /** Peers allowed to set X-Forwarded-For (IPs or IPv4 CIDRs); empty = any peer when trust is on. */
    private java.util.List<String> trustedProxies = new java.util.ArrayList<>();

    private long maxTrackedClients = 100_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    public boolean isTrustForwardedHeader() {
        return trustForwardedHeader;
    }

    public void setTrustForwardedHeader(boolean trustForwardedHeader) {
        this.trustForwardedHeader = trustForwardedHeader;
    }

    public java.util.List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(java.util.List<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    public long getMaxTrackedClients() {
        return maxTrackedClients;
    }

    public void setMaxTrackedClients(long maxTrackedClients) {
        this.maxTrackedClients = maxTrackedClients;
    }
}
