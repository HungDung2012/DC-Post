package com.dcpost.customer.security;

import com.dcpost.customer.auth.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

public class CustomerJwtFilter extends OncePerRequestFilter {
    private final AuthService auth;
    private final SecurityProblemWriter problems;
    public CustomerJwtFilter(AuthService auth, SecurityProblemWriter problems) {
        this.auth = auth;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !RequestPath.withinApplication(request).startsWith("/api/v1/customer/")
                && !RequestPath.withinApplication(request).equals("/api/v1/auth/logout");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null) {
            try {
                if (!authorization.startsWith("Bearer ")) throw new org.springframework.security.authentication.BadCredentialsException("Invalid bearer");
                var principal = auth.authenticate(authorization.substring(7));
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
            } catch (AuthenticationException | JwtException exception) {
                problems.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Phiên không hợp lệ hoặc đã hết hạn");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
