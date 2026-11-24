package com.dcpost.customer.integration.config;

import java.time.Duration;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("customer.integration")
public record IntegrationProperties(String pricingBaseUrl, String warehouseBaseUrl, String shippingBaseUrl,
        Duration connectTimeout, Duration readTimeout) {
    public IntegrationProperties {
        for (String url : new String[] {pricingBaseUrl, warehouseBaseUrl, shippingBaseUrl}) {
            URI uri = URI.create(url);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
                throw new IllegalArgumentException("Integration base URL must be an HTTP(S) endpoint without credentials");
        }
        if (connectTimeout == null || readTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero())
            throw new IllegalArgumentException("Integration timeouts must be positive");
    }
}
