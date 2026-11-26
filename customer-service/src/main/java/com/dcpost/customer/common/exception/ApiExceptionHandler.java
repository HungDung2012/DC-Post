package com.dcpost.customer.common.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import com.dcpost.customer.common.web.ApiProblems;
import com.dcpost.customer.integration.common.IntegrationException;
import com.dcpost.customer.shipment.service.ShipmentCreationException;
import com.dcpost.customer.shipment.service.ShipmentQueryException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ShipmentQueryException.class)
    ProblemDetail shipmentQuery(ShipmentQueryException exception, HttpServletRequest request) {
        var status = exception.code() == ShipmentQueryException.Code.SHIPMENT_NOT_FOUND
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        var detail = switch (exception.code()) {
            case SHIPMENT_NOT_FOUND -> "Không tìm thấy vận đơn";
            case INVALID_SHIPMENT_QUERY -> "Tham số tìm kiếm hoặc phân trang không hợp lệ";
            case FILTER_UNAVAILABLE -> "Bộ lọc này chưa khả dụng với dữ liệu tích hợp hiện tại";
        };
        return ApiProblems.create(request, status, exception.code().name(), detail);
    }

    @ExceptionHandler(ShipmentCreationException.class)
    ProblemDetail shipmentCreation(ShipmentCreationException exception, HttpServletRequest request) {
        var status = switch (exception.code()) {
            case INVALID_SHIPMENT -> HttpStatus.BAD_REQUEST;
            case FEE_CHANGED, IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
            case TRACKING_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        var detail = switch (exception.code()) {
            case INVALID_SHIPMENT -> "Thông tin vận đơn, bưu cục hoặc khóa yêu cầu không hợp lệ";
            case FEE_CHANGED -> "Phí vận chuyển đã thay đổi. Vui lòng kiểm tra và xác nhận lại";
            case IDEMPOTENCY_CONFLICT -> "Khóa yêu cầu đã được sử dụng với nội dung khác";
            case TRACKING_UNAVAILABLE -> "Chưa thể tạo mã vận đơn. Vui lòng thử lại với cùng khóa yêu cầu";
        };
        var problem = ApiProblems.create(request, status, exception.code().name(), detail);
        if (exception.currentFee() != null) problem.setProperty("currentFee", exception.currentFee());
        return problem;
    }

    @ExceptionHandler(IntegrationException.class)
    ProblemDetail integrationFailure(IntegrationException exception, HttpServletRequest request) {
        var status = switch (exception.kind()) {
            case INVALID_RESPONSE, UNAUTHORIZED -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ApiProblems.create(request, status, "EXTERNAL_" + exception.kind().name(),
                "Chưa thể xác minh thông tin từ dịch vụ liên quan. Vui lòng thử lại sau");
    }

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
