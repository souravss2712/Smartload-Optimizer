package com.smartload.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
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
