package com.dcpost.customer.auth.dto;

import java.util.Set;

public record CustomerAccount(Long id, String fullName, String email, String phone, Set<String> roles) {}
