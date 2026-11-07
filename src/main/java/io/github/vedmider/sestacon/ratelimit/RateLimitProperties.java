package io.github.vedmider.sestacon.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int requestsPerMinute = 60;
    private boolean trustForwardedHeader = false;
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

    public long getMaxTrackedClients() {
        return maxTrackedClients;
    }

    public void setMaxTrackedClients(long maxTrackedClients) {
        this.maxTrackedClients = maxTrackedClients;
    }
}
