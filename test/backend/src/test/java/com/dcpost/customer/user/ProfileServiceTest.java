package com.dcpost.customer.user;

import com.dcpost.customer.user.dto.UpdateProfileRequest;
import com.dcpost.customer.user.entity.User;
import com.dcpost.customer.user.repository.UserRepository;
import com.dcpost.customer.user.service.ProfileService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileServiceTest {
    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();
    private final UserRepository users = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC);
    private User user;
    private ProfileService service;

    @BeforeEach
    void setUp() {
        user = new User("Nguyen An", "an@example.com", "0912345678", encoder.encode("old-password"), clock.instant());
        ReflectionTestUtils.setField(user, "id", 42L);
        ReflectionTestUtils.setField(user, "authVersion", 1L);
        ReflectionTestUtils.setField(user, "refreshTokenHash", "a".repeat(64));
        ReflectionTestUtils.setField(user, "refreshExpiresAt", clock.instant().plusSeconds(604800));
        when(users.findById(42L)).thenReturn(Optional.of(user));
        when(users.findLockedById(42L)).thenReturn(Optional.of(user));
        service = new ProfileService(users, encoder, VALIDATION.getValidator(), clock);
    }

    @AfterAll static void closeValidation() { VALIDATION.close(); }

    /** Hồ sơ lấy đúng user theo principal và giữ địa chỉ mặc định nullable. */
    @Test
    void Test_getProfile_001() {
        var profile = service.getProfile(42L);
        assertNotNull(profile);
        assertEquals(42L, profile.id());
        assertEquals("an@example.com", profile.email());
        assertNull(profile.address());
    }

    /** Cập nhật tên, điện thoại và địa chỉ; email đăng nhập giữ nguyên. */
    @Test
    void Test_updateProfile_001() {
        var profile = service.updateProfile(42L, new UpdateProfileRequest("  New name ", "+84 987-654-321", "  New address "));
        assertNotNull(profile);
        assertEquals("New name", profile.fullName());
        assertEquals("0987654321", profile.phone());
        assertEquals("New address", profile.address());
        assertEquals("an@example.com", profile.email());
    }

    /** Địa chỉ rỗng được xóa về NULL. */
    @Test
    void Test_updateProfile_002() {
        assertNotNull(service.updateProfile(42L, new UpdateProfileRequest("Nguyen An", "0912345678", " ")));
        assertNull(user.getAddress());
    }

    /** Số điện thoại của tài khoản khác bị từ chối. */
    @Test
    void Test_updateProfile_003() {
        when(users.existsByPhone("0987654321")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.updateProfile(42L,
                new UpdateProfileRequest("Nguyen An", "0987654321", null)));
        assertEquals("0912345678", user.getPhone());
    }

    /** Dữ liệu hồ sơ không hợp lệ bị chặn trước khi ghi. */
    @Test
    void Test_updateProfile_004() {
        assertThrows(ConstraintViolationException.class, () -> service.updateProfile(42L,
                new UpdateProfileRequest(" ", "invalid", "x".repeat(501))));
    }

    /** Đổi mật khẩu hash lại và thu hồi cả access version lẫn refresh. */
    @Test
    void Test_changePassword_001() {
        service.changePassword(42L, "old-password", "new-password");
        assertTrue(encoder.matches("new-password", user.getPasswordHash()));
        assertEquals(2, user.getAuthVersion());
        assertNull(user.getRefreshTokenHash());
        assertNull(user.getRefreshExpiresAt());
    }

    /** Mật khẩu hiện tại không đúng không làm thay đổi hash hoặc phiên. */
    @Test
    void Test_changePassword_002() {
        assertThrows(IllegalArgumentException.class, () -> service.changePassword(42L, "wrong-password", "new-password"));
        assertTrue(encoder.matches("old-password", user.getPasswordHash()));
        assertEquals(1, user.getAuthVersion());
    }

    /** Mật khẩu mới không hợp lệ không được lưu. */
    @Test
    void Test_changePassword_003() {
        assertThrows(ConstraintViolationException.class, () -> service.changePassword(42L, "old-password", "short"));
    }

    /** Duplicate key do request đồng thời được đổi thành account conflict. */
    @Test
    void Test_updateProfile_005() {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate",
                new java.sql.SQLIntegrityConstraintViolationException("duplicate", "23000", 1062)))
                .when(users).flush();
        assertThrows(com.dcpost.customer.common.exception.AccountConflictException.class,
                () -> service.updateProfile(42L, new UpdateProfileRequest("New name", "0987654321", null)));
    }

    /** Lỗi integrity khác duplicate không bị đổi nhầm thành lỗi thông tin trùng. */
    @Test
    void Test_updateProfile_006() {
        var failure = new org.springframework.dao.DataIntegrityViolationException("different integrity failure");
        doThrow(failure).when(users).flush();
        assertSame(failure, assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> service.updateProfile(42L, new UpdateProfileRequest("New name", "0987654321", null))));
    }
}
