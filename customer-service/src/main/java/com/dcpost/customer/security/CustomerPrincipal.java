package com.dcpost.customer.security;

public record CustomerPrincipal(long userId, long authVersion) {}
