package com.smartload.exception;

import com.smartload.model.OptimizeRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        if (exception.getBindingResult().getTarget() instanceof OptimizeRequest optimizeRequest
                && optimizeRequest.getOrders() != null
                && optimizeRequest.getOrders().size() > OptimizeRequest.MAX_ORDERS) {
            String message = "orders cannot contain more than " + OptimizeRequest.MAX_ORDERS + " items";
            return build(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    message,
                    List.of(message),
                    request.getRequestURI());
        }

        List<String> errors = exception.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> {
                    if (error instanceof FieldError fieldError) {
                        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
                    }
                    return error.getDefaultMessage();
                })
                .toList();

        return build(HttpStatus.BAD_REQUEST, "Validation failed", errors, request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "Malformed JSON request",
                List.of("Request body could not be parsed"),
                request.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        List<String> errors = exception.getConstraintViolations().isEmpty()
                ? List.of(exception.getMessage())
                : exception.getConstraintViolations()
                        .stream()
                        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                        .toList();

        return build(HttpStatus.BAD_REQUEST, "Validation failed", errors, request.getRequestURI());
    }

    @ExceptionHandler(TooManyOrdersException.class)
    public ResponseEntity<ErrorResponse> handleTooManyOrders(
            TooManyOrdersException exception,
            HttpServletRequest request) {
        return build(
                HttpStatus.PAYLOAD_TOO_LARGE,
                exception.getMessage(),
                List.of(exception.getMessage()),
                request.getRequestURI());
    }

    @ExceptionHandler(ArithmeticException.class)
    public ResponseEntity<ErrorResponse> handleArithmetic(
            ArithmeticException exception,
            HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "Numeric input is too large",
                List.of(exception.getMessage()),
                request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        log.error(
                "Unexpected error while handling {} {}: {}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage(),
                exception);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected server error",
                List.of("An unexpected error occurred"),
                request.getRequestURI());
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String message,
            List<String> errors,
            String path) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), message, errors, path));
    }

    public record ErrorResponse(
            Instant timestamp,
            int status,
            String message,
            List<String> errors,
            String path) {
    }
}
