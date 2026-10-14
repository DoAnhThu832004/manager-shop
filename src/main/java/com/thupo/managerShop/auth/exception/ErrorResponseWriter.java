package com.thupo.managerShop.auth.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Ghi response lỗi JSON trực tiếp ra servlet response. Dùng cho các filter và entry point,
 * nơi {@code @RestControllerAdvice} không với tới được.
 */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      ErrorCode errorCode, Long retryAfterSeconds) throws IOException {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                errorCode.getStatus().value(),
                errorCode.name(),
                errorCode.getDefaultMessage(),
                retryAfterSeconds,
                request.getRequestURI());
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (retryAfterSeconds != null) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        }
        objectMapper.writeValue(response.getWriter(), body);
    }
}
