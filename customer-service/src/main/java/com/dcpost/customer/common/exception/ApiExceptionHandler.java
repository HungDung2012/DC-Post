package com.dcpost.customer.common.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import com.dcpost.customer.common.web.ApiProblems;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(PasswordMismatchException.class)
    ProblemDetail passwordMismatch(PasswordMismatchException exception, HttpServletRequest request) {
        return ApiProblems.create(request, HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", exception.getMessage());
    }
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    ProblemDetail unauthenticated(HttpServletRequest request) {
        return ApiProblems.create(request, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Thông tin đăng nhập hoặc phiên không hợp lệ");
    }

    @ExceptionHandler(AccountConflictException.class)
    ProblemDetail accountConflict(AccountConflictException exception, HttpServletRequest request) {
        return ApiProblems.create(request, HttpStatus.CONFLICT, "ACCOUNT_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return validationProblem(errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalidInput(ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return validationProblem(errors, request);
    }

    private ProblemDetail validationProblem(Map<String, String> errors, HttpServletRequest request) {
        var problem = ApiProblems.create(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ");
        problem.setProperty("errors", errors);
        return problem;
    }
}
