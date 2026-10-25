package com.thupo.managerShop.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Chuyển mọi exception thành response lỗi thống nhất, thông báo tiếng Việt. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthException ex, HttpServletRequest request) {
        return build(ex.getErrorCode(), ex.getMessage(), ex.getRetryAfterSeconds(), ex.getCaptchaRequired(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(ErrorCode.VALIDATION_ERROR, messageWithDetail(detail), null, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex,
                                                          HttpServletRequest request) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return build(ErrorCode.VALIDATION_ERROR, messageWithDetail(detail), null, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleMalformed(Exception ex, HttpServletRequest request) {
        // Không trả chi tiết parse lỗi ra ngoài để tránh lộ cấu trúc nội bộ.
        return build(ErrorCode.VALIDATION_ERROR, null, null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(ErrorCode.FORBIDDEN, null, null, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, null, null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                HttpServletRequest request) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, null, null, request);
    }

    /** Lưới an toàn cuối: ghi log đầy đủ ở server, chỉ trả thông báo chung cho client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Lỗi không mong đợi tại {}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, null, null, request);
    }

    private String messageWithDetail(String detail) {
        return detail.isBlank()
                ? ErrorCode.VALIDATION_ERROR.getDefaultMessage()
                : ErrorCode.VALIDATION_ERROR.getDefaultMessage() + " (" + detail + ")";
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message, Long retryAfter,
                                                HttpServletRequest request) {
        return build(code, message, retryAfter, null, request);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message, Long retryAfter,
                                                Boolean captchaRequired,
                                                HttpServletRequest request) {
        HttpStatus status = code.getStatus();
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                code.name(),
                message != null ? message : code.getDefaultMessage(),
                retryAfter,
                request.getRequestURI(),
                captchaRequired);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (retryAfter != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        }
        return builder.body(body);
    }
}
