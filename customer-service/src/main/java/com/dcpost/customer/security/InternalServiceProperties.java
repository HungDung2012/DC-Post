package com.dcpost.customer.security;

import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("customer.internal")
public record InternalServiceProperties(Map<String, Credential> services) {
    public InternalServiceProperties { services = services == null ? Map.of() : Map.copyOf(services); }
    public record Credential(String apiKey, Set<String> permissions) {
        public Credential { permissions = permissions == null ? Set.of() : Set.copyOf(permissions); }
        @Override public String toString() { return "Credential[redacted]"; }
    }
}
