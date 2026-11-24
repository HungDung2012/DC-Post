package com.dcpost.customer.auth.controller;

import com.dcpost.customer.auth.dto.AuthSession;
import com.dcpost.customer.auth.dto.LoginRequest;
import com.dcpost.customer.auth.service.AuthService;
import com.dcpost.customer.security.CustomerPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class SessionController {
    private final AuthService auth;
    private final boolean cookieSecure;
    private final Clock clock;

    public SessionController(AuthService auth, @Value("${customer.auth.cookie-secure}") boolean cookieSecure, Clock clock) {
        this.auth = auth;
        this.cookieSecure = cookieSecure;
        this.clock = clock;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken()); }

    @PostMapping("/login")
    public AuthSession login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        var session = auth.login(request.email(), request.password());
        writeRefresh(response, session);
        return session;
    }

    @PostMapping("/refresh")
    public AuthSession refresh(@CookieValue(name = "refresh_token", required = false) String refreshToken,
                               HttpServletResponse response) {
        var session = auth.refresh(refreshToken);
        writeRefresh(response, session);
        return session;
    }

    @PostMapping("/logout")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal CustomerPrincipal principal, HttpServletResponse response) {
        auth.logout(principal.userId(), principal.authVersion());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("").maxAge(Duration.ZERO).build().toString());
    }

    private void writeRefresh(HttpServletResponse response, AuthSession session) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(session.refreshToken())
                .maxAge(Duration.between(clock.instant(), session.refreshExpiresAt())).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String value) {
        return ResponseCookie.from("refresh_token", value).httpOnly(true).secure(cookieSecure)
                .sameSite("Lax").path("/api/v1/auth");
    }
}
