package com.dcpost.customer.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthRequestGuardFilter extends OncePerRequestFilter {
    private final Set<String> origins;
    private final RequestRateLimiter limiter;
    private final SecurityProblemWriter problems;

    public AuthRequestGuardFilter(Set<String> origins, RequestRateLimiter limiter, SecurityProblemWriter problems) {
        this.origins = origins;
        this.limiter = limiter;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = RequestPath.withinApplication(request);
        boolean authWrite = path.startsWith("/api/v1/auth/") && !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        String origin = request.getHeader("Origin");
        if (authWrite && (origin == null || !origins.contains(origin))) {
            problems.write(request, response, HttpStatus.FORBIDDEN, "ORIGIN_REJECTED", "Nguồn yêu cầu không hợp lệ");
            return;
        }
        if ((authWrite && !path.equals("/api/v1/auth/logout")) || path.startsWith("/api/v1/public/tracking")) {
            String group = authWrite ? "auth:" : "tracking:";
            var decision = limiter.allow(group + request.getRemoteAddr());
            if (!decision.allowed()) {
                response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
                problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Vui lòng thử lại sau");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
