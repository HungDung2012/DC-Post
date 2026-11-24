package com.dcpost.customer.auth;

import com.dcpost.customer.auth.service.AuthService;
import com.dcpost.customer.security.JwtTokenService;
import com.dcpost.customer.security.RefreshTokenService;
import com.dcpost.customer.user.entity.Role;
import com.dcpost.customer.user.entity.User;
import com.dcpost.customer.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");
    private final UserRepository users = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final RefreshTokenService refreshTokens = new RefreshTokenService();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtTokenService jwt = new JwtTokenService("isolated-auth-test-signing-secret-at-least-32-bytes",
            Duration.ofHours(24), clock);
    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("Nguyen An", "an@example.com", "0912345678", encoder.encode("safe-password"), NOW);
        ReflectionTestUtils.setField(user, "id", 42L);
        Role role = mock(Role.class);
        when(role.getRoleName()).thenReturn("CUSTOMER");
        user.assignRole(role);
        when(users.findLockedByEmail("an@example.com")).thenReturn(Optional.of(user));
        when(users.findLockedById(42L)).thenReturn(Optional.of(user));
        when(users.findById(42L)).thenReturn(Optional.of(user));
        service = new AuthService(users, encoder, jwt, refreshTokens, clock, Duration.ofDays(7));
    }

    /** Login phát access 24 giờ, refresh 7 ngày và chỉ lưu hash refresh. */
    @Test
    void Test_login_001() {
        var result = service.login(" AN@Example.com ", "safe-password");
        assertNotNull(result);
        assertEquals(1, user.getAuthVersion());
        assertEquals(NOW.plusSeconds(86400), result.expiresAt());
        assertEquals(NOW.plus(Duration.ofDays(7)), user.getRefreshExpiresAt());
        assertNotEquals(result.refreshToken(), user.getRefreshTokenHash());
        assertTrue(refreshTokens.matches(result.refreshToken(), user.getRefreshTokenHash()));
        assertEquals(42L, jwt.verify(result.accessToken()).userId());
    }

    /** Sai mật khẩu không làm thay đổi phiên tài khoản. */
    @Test
    void Test_login_002() {
        assertThrows(BadCredentialsException.class, () -> service.login("an@example.com", "wrong-password"));
        assertEquals(0, user.getAuthVersion());
        assertNull(user.getRefreshTokenHash());
    }

    /** Tài khoản khóa không được đăng nhập dù mật khẩu đúng. */
    @Test
    void Test_login_003() {
        ReflectionTestUtils.setField(user, "active", false);
        assertThrows(BadCredentialsException.class, () -> service.login("an@example.com", "safe-password"));
    }

    /** Login mới thu hồi access và refresh của phiên trước. */
    @Test
    void Test_login_004() {
        var first = service.login("an@example.com", "safe-password");
        assertNotNull(first);
        var second = service.login("an@example.com", "safe-password");
        assertNotNull(second);
        assertEquals(2, user.getAuthVersion());
        assertThrows(BadCredentialsException.class, () -> service.authenticate(first.accessToken()));
        assertThrows(BadCredentialsException.class, () -> service.refresh(first.refreshToken()));
        assertEquals(42L, service.authenticate(second.accessToken()).userId());
    }

    /** Refresh xoay token nguyên tử và giữ auth_version của cùng phiên. */
    @Test
    void Test_refresh_001() {
        var first = service.login("an@example.com", "safe-password");
        assertNotNull(first);
        var next = service.refresh(first.refreshToken());
        assertNotNull(next);
        assertNotEquals(first.refreshToken(), next.refreshToken());
        assertEquals(1, user.getAuthVersion());
        assertThrows(BadCredentialsException.class, () -> service.refresh(first.refreshToken()));
        assertTrue(refreshTokens.matches(next.refreshToken(), user.getRefreshTokenHash()));
    }

    /** Refresh hết hạn bị từ chối và không được tự gia hạn. */
    @Test
    void Test_refresh_002() {
        var first = service.login("an@example.com", "safe-password");
        assertNotNull(first);
        ReflectionTestUtils.setField(user, "refreshExpiresAt", NOW.minusSeconds(1));
        assertThrows(BadCredentialsException.class, () -> service.refresh(first.refreshToken()));
    }

    /** Refresh không đúng cấu trúc không được dùng để query tài khoản. */
    @Test
    void Test_refresh_003() {
        assertThrows(BadCredentialsException.class, () -> service.refresh("invalid"));
        verify(users, never()).findLockedById(anyLong());
    }

    /** Logout thu hồi ngay access và xóa hash/expiry của refresh. */
    @Test
    void Test_logout_001() {
        var session = service.login("an@example.com", "safe-password");
        assertNotNull(session);
        service.logout(42L, 1);
        assertEquals(2, user.getAuthVersion());
        assertNull(user.getRefreshTokenHash());
        assertNull(user.getRefreshExpiresAt());
        assertThrows(BadCredentialsException.class, () -> service.authenticate(session.accessToken()));
    }

    /** Request logout từ phiên cũ không được thu hồi phiên mới. */
    @Test
    void Test_logout_002() {
        service.login("an@example.com", "safe-password");
        service.login("an@example.com", "safe-password");
        assertThrows(BadCredentialsException.class, () -> service.logout(42L, 1));
        assertEquals(2, user.getAuthVersion());
        assertNotNull(user.getRefreshTokenHash());
    }

    /** Token ký đúng vẫn bị từ chối khi tài khoản đã khóa. */
    @Test
    void Test_authenticate_001() {
        var session = service.login("an@example.com", "safe-password");
        assertNotNull(session);
        ReflectionTestUtils.setField(user, "active", false);
        assertThrows(BadCredentialsException.class, () -> service.authenticate(session.accessToken()));
    }

    /** Tài khoản không còn CUSTOMER bị chặn dù token còn hạn và version khớp. */
    @Test
    void Test_authenticate_002() {
        var session = service.login("an@example.com", "safe-password");
        ReflectionTestUtils.setField(user, "roles", new java.util.HashSet<Role>());
        assertThrows(BadCredentialsException.class, () -> service.authenticate(session.accessToken()));
    }
}
