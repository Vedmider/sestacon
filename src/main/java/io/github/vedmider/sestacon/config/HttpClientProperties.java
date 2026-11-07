package io.github.vedmider.sestacon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Timeouts for outbound HTTP to the LLM and embedding APIs. Without these a hung upstream
 * would block the request thread indefinitely and exhaust the Tomcat pool.
 */
@ConfigurationProperties(prefix = "app.http")
public class HttpClientProperties {

    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(60);

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
