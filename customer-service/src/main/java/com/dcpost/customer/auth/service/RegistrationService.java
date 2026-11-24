package com.dcpost.customer.auth.service;

import com.dcpost.customer.auth.dto.CustomerAccount;
import com.dcpost.customer.auth.dto.RegisterRequest;
import com.dcpost.customer.common.exception.AccountConflictException;
import com.dcpost.customer.common.exception.PersistenceConflicts;
import com.dcpost.customer.user.entity.User;
import com.dcpost.customer.user.entity.Role;
import com.dcpost.customer.user.repository.RoleRepository;
import com.dcpost.customer.user.repository.UserRepository;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class RegistrationService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final Validator validator;

    public RegistrationService(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                               Validator validator, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.clock = clock;
        this.validator = validator;
    }

    @Transactional
    public CustomerAccount register(RegisterRequest request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        if (users.existsByEmail(request.email()) || users.existsByPhone(request.phone())) {
            throw new AccountConflictException();
        }
        var customer = roles.findByRoleName("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role is not configured"));
        var user = new User(request.fullName(), request.email(), request.phone(),
                encoder.encode(request.password()), clock.instant());
        user.assignRole(customer);
        User saved;
        try {
            saved = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw PersistenceConflicts.accountConflict(exception);
        }
        return new CustomerAccount(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getPhone(),
                saved.getRoles().stream().map(Role::getRoleName).collect(Collectors.toUnmodifiableSet()));
    }
}
