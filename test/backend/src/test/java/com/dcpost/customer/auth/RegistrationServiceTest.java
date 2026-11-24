package com.dcpost.customer.auth;

import com.dcpost.customer.auth.dto.RegisterRequest;
import com.dcpost.customer.auth.service.RegistrationService;
import com.dcpost.customer.user.entity.Role;
import com.dcpost.customer.user.entity.User;
import com.dcpost.customer.user.repository.RoleRepository;
import com.dcpost.customer.user.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistrationServiceTest {
    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC);
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(users, roles, encoder, VALIDATION.getValidator(), clock);
    }

    @AfterAll
    static void closeValidation() { VALIDATION.close(); }

    /** Đăng ký chuẩn hóa thông tin, hash mật khẩu thật và chỉ gán CUSTOMER. */
    @Test
    void Test_register_001() {
        Role customer = mock(Role.class);
        when(customer.getRoleName()).thenReturn("CUSTOMER");
        when(roles.findByRoleName("CUSTOMER")).thenReturn(Optional.of(customer));
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.register(new RegisterRequest("  Nguyen An  ", "  AN@Example.com ",
                "+84 912-345-678", "safe-password"));

        assertNotNull(result);
        assertEquals("Nguyen An", result.fullName());
        assertEquals("an@example.com", result.email());
        assertEquals("0912345678", result.phone());
        assertEquals(Set.of("CUSTOMER"), result.roles());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertNotEquals("safe-password", saved.getValue().getPasswordHash());
        assertTrue(encoder.matches("safe-password", saved.getValue().getPasswordHash()));
        assertEquals(clock.instant(), saved.getValue().getCreatedAt());
        assertEquals(Set.of(customer), saved.getValue().getRoles());
    }

    /** Email đã tồn tại bị từ chối trước khi lưu hoặc hash mật khẩu. */
    @Test
    void Test_register_002() {
        when(users.existsByEmail("an@example.com")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.register(validRequest()));
        verify(users, never()).saveAndFlush(any());
    }

    /** Số điện thoại đã tồn tại bị từ chối sau khi chuẩn hóa. */
    @Test
    void Test_register_003() {
        when(users.existsByPhone("0912345678")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.register(validRequest()));
        verify(users, never()).saveAndFlush(any());
    }

    /** Mật khẩu dưới tám ký tự bị từ chối ở service, kể cả không qua controller. */
    @Test
    void Test_register_004() {
        assertThrows(ConstraintViolationException.class, () -> service.register(
                new RegisterRequest("Nguyen An", "an@example.com", "0912345678", "short")));
        verifyNoInteractions(users, roles);
    }

    /** Constraint database chặn race và được chuyển thành lỗi trùng thông tin. */
    @Test
    void Test_register_005() {
        Role customer = mock(Role.class);
        when(roles.findByRoleName("CUSTOMER")).thenReturn(Optional.of(customer));
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate",
                new java.sql.SQLIntegrityConstraintViolationException("duplicate", "23000", 1062)));
        assertThrows(IllegalArgumentException.class, () -> service.register(validRequest()));
    }

    /** Đầu vào không hợp lệ không được chạm database. */
    @Test
    void Test_register_006() {
        assertThrows(ConstraintViolationException.class, () -> service.register(
                new RegisterRequest(" ", "invalid-email", "bad-phone", "safe-password")));
        verifyNoInteractions(users, roles);
    }

    /** Mật khẩu Unicode vượt giới hạn 72 byte của BCrypt bị từ chối rõ ràng. */
    @Test
    void Test_register_007() {
        assertThrows(ConstraintViolationException.class, () -> service.register(
                new RegisterRequest("Nguyen An", "an@example.com", "0912345678", "ậ".repeat(30))));
        verifyNoInteractions(users, roles);
    }

    private RegisterRequest validRequest() {
        return new RegisterRequest("Nguyen An", "an@example.com", "0912345678", "safe-password");
    }
}
