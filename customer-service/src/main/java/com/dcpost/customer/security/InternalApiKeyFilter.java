package com.dcpost.customer.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class InternalApiKeyFilter extends OncePerRequestFilter {
    public record ServicePrincipal(String serviceName) {}
    private final InternalServiceProperties properties;
    private final SecurityProblemWriter problems;

    public InternalApiKeyFilter(InternalServiceProperties properties, SecurityProblemWriter problems) {
        this.properties = properties;
        this.problems = problems;
        List<String> enabledKeys = properties.services().values().stream().map(InternalServiceProperties.Credential::apiKey)
                .filter(key -> key != null && !key.isBlank()).toList();
        if (enabledKeys.stream().distinct().count() != enabledKeys.size()) {
            throw new IllegalArgumentException("Internal services must have distinct API keys");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !RequestPath.withinApplication(request).startsWith("/api/v1/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader("X-API-Key");
        if (provided != null && !provided.isBlank()) {
            for (var entry : properties.services().entrySet()) {
                var credential = entry.getValue();
                if (credential.apiKey() != null && !credential.apiKey().isBlank() && MessageDigest.isEqual(
                        provided.getBytes(StandardCharsets.UTF_8), credential.apiKey().getBytes(StandardCharsets.UTF_8))) {
                    var authorities = credential.permissions().stream().map(SimpleGrantedAuthority::new).toList();
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            new ServicePrincipal(entry.getKey()), null, authorities));
                    chain.doFilter(request, response);
                    return;
                }
            }
        }
        problems.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Service chưa được xác thực");
    }
}
