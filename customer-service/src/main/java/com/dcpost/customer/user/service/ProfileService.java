package com.dcpost.customer.user.service;

import com.dcpost.customer.user.dto.CustomerProfile;
import com.dcpost.customer.user.dto.UpdateProfileRequest;
import com.dcpost.customer.user.dto.ChangePasswordRequest;
import com.dcpost.customer.user.entity.User;
import com.dcpost.customer.common.exception.AccountConflictException;
import com.dcpost.customer.common.exception.PasswordMismatchException;
import com.dcpost.customer.common.exception.PersistenceConflicts;
import com.dcpost.customer.user.repository.UserRepository;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Validator validator;
    private final Clock clock;

    public ProfileService(UserRepository users, PasswordEncoder encoder, Validator validator, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CustomerProfile getProfile(long userId) {
        return profile(users.findById(userId).orElseThrow(() -> new BadCredentialsException("Tài khoản không tồn tại")));
    }

    @Transactional
    public CustomerProfile updateProfile(long userId, UpdateProfileRequest request) {
        validate(request);
        var user = users.findLockedById(userId).orElseThrow(() -> new BadCredentialsException("Tài khoản không tồn tại"));
        if (!user.getPhone().equals(request.phone()) && users.existsByPhone(request.phone())) {
            throw new AccountConflictException();
        }
        user.updateProfile(request.fullName(), request.phone(), request.address(), clock.instant());
        try {
            users.flush();
        } catch (DataIntegrityViolationException exception) {
            throw PersistenceConflicts.accountConflict(exception);
        }
        return profile(user);
    }

    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword) {
        validate(new ChangePasswordRequest(currentPassword, newPassword));
        var user = users.findLockedById(userId).orElseThrow(() -> new BadCredentialsException("Tài khoản không tồn tại"));
        if (!encoder.matches(currentPassword, user.getPasswordHash())) throw new PasswordMismatchException();
        user.changePassword(encoder.encode(newPassword), clock.instant());
    }

    private <T> void validate(T request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
    }

    private CustomerProfile profile(User user) {
        return new CustomerProfile(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getAddress(), user.getCreatedAt());
    }
}
