package com.dcpost.customer.common.exception;

public class AccountConflictException extends IllegalArgumentException {
    public AccountConflictException() {
        super("Email hoặc số điện thoại đã được sử dụng");
    }
}
