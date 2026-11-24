package com.dcpost.customer.auth.service;

import com.dcpost.customer.auth.dto.AuthSession;
import com.dcpost.customer.auth.dto.CustomerAccount;
import com.dcpost.customer.security.CustomerPrincipal;
import com.dcpost.customer.security.JwtTokenService;
import com.dcpost.customer.security.RefreshTokenService;
import com.dcpost.customer.user.repository.UserRepository;
import com.dcpost.customer.user.entity.User;
import com.dcpost.customer.user.entity.Role;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtTokenService jwt;
    private final RefreshTokenService refreshTokens;
    private final Clock clock;
    private final Duration refreshDuration;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtTokenService jwt,
                       RefreshTokenService refreshTokens, Clock clock,
                       @Value("${customer.auth.refresh-token-duration}") Duration refreshDuration) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
        this.refreshDuration = refreshDuration;
    }

    @Transactional
    public AuthSession login(String email, String password) {
        if (email == null || password == null) throw invalidSession();
        var user = users.findLockedByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(this::invalidSession);
        requireCustomer(user);
        if (!encoder.matches(password, user.getPasswordHash())) throw invalidSession();
        String refresh = refreshTokens.issue(user.getId());
        user.startSession(refreshTokens.hash(refresh), clock.instant().plus(refreshDuration), clock.instant());
        return session(user, refresh);
    }

    @Transactional
    public AuthSession refresh(String token) {
        var user = users.findLockedById(refreshTokens.userId(token)).orElseThrow(this::invalidSession);
        requireCustomer(user);
        if (user.getRefreshExpiresAt() == null || !clock.instant().isBefore(user.getRefreshExpiresAt())
                || !refreshTokens.matches(token, user.getRefreshTokenHash())) throw invalidSession();
        String next = refreshTokens.issue(user.getId());
        user.rotateRefresh(refreshTokens.hash(next), clock.instant().plus(refreshDuration), clock.instant());
        return session(user, next);
    }

    @Transactional
    public void logout(long userId, long authVersion) {
        var user = users.findLockedById(userId).orElseThrow(this::invalidSession);
        requireCustomer(user);
        if (user.getAuthVersion() != authVersion) throw invalidSession();
        user.revokeSession(clock.instant());
    }

    @Transactional(readOnly = true)
    public CustomerPrincipal authenticate(String token) {
        var identity = jwt.verify(token);
        var user = users.findById(identity.userId()).orElseThrow(this::invalidSession);
        requireCustomer(user);
        if (user.getAuthVersion() != identity.authVersion()) throw invalidSession();
        return new CustomerPrincipal(user.getId(), user.getAuthVersion());
    }

    private AuthSession session(User user, String refresh) {
        String access = jwt.issue(user.getId(), user.getAuthVersion(), clock.instant());
        var account = new CustomerAccount(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getRoles().stream().map(Role::getRoleName).collect(Collectors.toUnmodifiableSet()));
        return new AuthSession(access, jwt.verify(access).expiresAt(), account, refresh, user.getRefreshExpiresAt());
    }

    private void requireCustomer(User user) {
        if (!user.isActive() || user.getRoles().stream().noneMatch(role -> "CUSTOMER".equals(role.getRoleName()))) {
            throw invalidSession();
        }
    }

    private BadCredentialsException invalidSession() {
        return new BadCredentialsException("Thông tin đăng nhập hoặc phiên không hợp lệ");
    }
}
