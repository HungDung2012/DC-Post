package com.dcpost.customer.user.dto;

import java.time.Instant;

public record CustomerProfile(long id, String fullName, String email, String phone, String address, Instant createdAt) {}
