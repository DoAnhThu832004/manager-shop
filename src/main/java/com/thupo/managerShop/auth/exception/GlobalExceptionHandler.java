package com.thupo.managerShop.auth.exception;

import com.thupo.managerShop.common.exception.BadRequestAlertException;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.common.exception.ForbiddenAlertException;
import com.thupo.managerShop.common.exception.ResourceNotFoundException;
import com.thupo.managerShop.common.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 
 * Chuyển mọi exception thành response lỗi thống nhất:
 * - Module Auth: ErrorResponse chuẩn cũ.
 * - Module Nghiệp vụ (Products, Inventory...): RFC 7807 ProblemDetail (application/problem+json).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthException ex, HttpServletRequest request) {
        return build(ex.getErrorCode(), ex.getMessage(), ex.getRetryAfterSeconds(), ex.getCaptchaRequired(), request);
    }

    @ExceptionHandler(BadRequestAlertException.class)
    public ResponseEntity<ProblemDetail> handleBadRequestAlert(BadRequestAlertException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("about:blank"));
        pd.setTitle("Bad Request");
        pd.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ConflictException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("about:blank"));
        pd.setTitle("Conflict");
        pd.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("about:blank"));
        pd.setTitle("Not Found");
        pd.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @ExceptionHandler(ForbiddenAlertException.class)
    public ResponseEntity<ProblemDetail> handleForbiddenAlert(ForbiddenAlertException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setType(URI.create("about:blank"));
        pd.setTitle("Forbidden");
        pd.setInstance(URI.create(request.getRequestURI()));
        if (!ex.getRequiredPermissions().isEmpty()) {
            pd.setProperty("requiredPermissions", ex.getRequiredPermissions());
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/v1/auth")) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ");
            pd.setType(URI.create("about:blank"));
            pd.setTitle("Bad Request");
            pd.setInstance(URI.create(request.getRequestURI()));
            List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                    .map(f -> Map.of("field", f.getField(), "message", f.getDefaultMessage() != null ? f.getDefaultMessage() : ""))
                    .toList();
            pd.setProperty("errors", fieldErrors);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(pd);
        }

        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(ErrorCode.VALIDATION_ERROR, messageWithDetail(detail), null, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/v1/auth")) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Ràng buộc dữ liệu không hợp lệ");
            pd.setType(URI.create("about:blank"));
            pd.setTitle("Bad Request");
            pd.setInstance(URI.create(request.getRequestURI()));
            List<Map<String, String>> violations = ex.getConstraintViolations().stream()
                    .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                    .toList();
            pd.setProperty("errors", violations);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(pd);
        }

        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return build(ErrorCode.VALIDATION_ERROR, messageWithDetail(detail), null, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public Object handleMalformed(Exception ex, HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/v1/auth")) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Định dạng yêu cầu không hợp lệ hoặc không được hỗ trợ");
            pd.setType(URI.create("about:blank"));
            pd.setTitle("Bad Request");
            pd.setInstance(URI.create(request.getRequestURI()));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(pd);
        }
        return build(ErrorCode.VALIDATION_ERROR, null, null, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Lỗi toàn vẹn dữ liệu (DataIntegrityViolationException) tại {}: {}", request.getRequestURI(), ex.getMessage());
        String msg = ex.getMessage();
        boolean isDuplicate = msg != null && (msg.contains("Duplicate entry") || msg.contains("duplicate key") || msg.contains("23505"));

        if (!request.getRequestURI().startsWith("/api/v1/auth")) {
            HttpStatus status = isDuplicate ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
            String detail = isDuplicate ? "Dữ liệu bị trùng lặp trong hệ thống" : "Dữ liệu vi phạm ràng buộc toàn vẹn";
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
            pd.setType(URI.create("about:blank"));
            pd.setTitle(isDuplicate ? "Conflict" : "Bad Request");
            pd.setInstance(URI.create(request.getRequestURI()));
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(pd);
        }

        if (isDuplicate) {
            return build(ErrorCode.DATA_CONFLICT, "Dữ liệu đã tồn tại trong hệ thống (trùng lặp bản ghi)", null, request);
        }
        return build(ErrorCode.VALIDATION_ERROR, "Dữ liệu gửi lên vi phạm ràng buộc toàn vẹn của hệ thống", null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, HandlerMethod handlerMethod) {
        if (!request.getRequestURI().startsWith("/api/v1/auth")) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này");
            pd.setType(URI.create("about:blank"));
            pd.setTitle("Forbidden");
            pd.setInstance(URI.create(request.getRequestURI()));

            List<String> perms = List.of();
            if (handlerMethod != null && handlerMethod.hasMethodAnnotation(RequirePermission.class)) {
                perms = List.of(handlerMethod.getMethodAnnotation(RequirePermission.class).value());
            } else if (request.getRequestURI().startsWith("/api/v1/products")
                    || request.getRequestURI().startsWith("/api/v1/collections")) {
                perms = List.of("MANAGE_PRODUCTS");
            } else if (request.getRequestURI().startsWith("/api/v1/inventory")
                    || request.getRequestURI().startsWith("/api/v1/purchase-orders")
                    || request.getRequestURI().startsWith("/api/v1/transfers")) {
                perms = List.of("MANAGE_INVENTORY");
            } else if (request.getRequestURI().startsWith("/api/v1/gift-cards")) {
                perms = List.of("MANAGE_GIFT_CARDS");
            }

            if (!perms.isEmpty()) {
                pd.setProperty("requiredPermissions", perms);
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(pd);
        }
        return build(ErrorCode.FORBIDDEN, null, null, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/v1/auth")) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu yêu cầu");
            pd.setType(URI.create("about:blank"));
            pd.setTitle("Not Found");
            pd.setInstance(URI.create(request.getRequestURI()));
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(pd);
        }
        return build(ErrorCode.NOT_FOUND, null, null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/v1/auth")) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức HTTP không được hỗ trợ");
            pd.setType(URI.create("about:blank"));
            pd.setTitle("Method Not Allowed");
            pd.setInstance(URI.create(request.getRequestURI()));
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(pd);
        }
        return build(ErrorCode.METHOD_NOT_ALLOWED, null, null, request);
    }

    /** Lưới an toàn cuối: ghi log đầy đủ ở server, chỉ trả thông báo chung cho client. */
    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Lỗi không mong đợi tại {}", request.getRequestURI(), ex);
        if (!request.getRequestURI().startsWith("/api/v1/auth")) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Hệ thống gặp sự cố, vui lòng thử lại sau");
            pd.setType(URI.create("about:blank"));
            pd.setTitle("Internal Server Error");
            pd.setInstance(URI.create(request.getRequestURI()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(pd);
        }
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
