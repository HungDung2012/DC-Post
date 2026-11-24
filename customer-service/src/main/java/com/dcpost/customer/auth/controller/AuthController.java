package com.dcpost.customer.auth.controller;

import com.dcpost.customer.auth.dto.RegisterRequest;
import com.dcpost.customer.auth.dto.CustomerAccount;
import com.dcpost.customer.auth.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final RegistrationService registration;

    public AuthController(RegistrationService registration) { this.registration = registration; }

    @PostMapping("/register")
    public ResponseEntity<CustomerAccount> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(registration.register(request));
    }
}
