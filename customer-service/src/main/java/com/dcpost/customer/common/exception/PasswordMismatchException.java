package com.dcpost.customer.common.exception;

public class PasswordMismatchException extends IllegalArgumentException {
    public PasswordMismatchException() { super("Mật khẩu hiện tại không đúng"); }
}
