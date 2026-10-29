package com.thupo.managerShop.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thupo.managerShop.auth.exception.ErrorCode;
import com.thupo.managerShop.auth.exception.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 
 * Trả 403 dạng JSON thống nhất khi đã đăng nhập nhưng thiếu quyền:
 * - Module Auth: ErrorResponse
 * - Module Nghiệp vụ: RFC 7807 ProblemDetail có trường requiredPermissions
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter errorResponseWriter;
    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ErrorResponseWriter errorResponseWriter, ObjectMapper objectMapper) {
        this.errorResponseWriter = errorResponseWriter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/products") || uri.startsWith("/api/v1/collections")
                || uri.startsWith("/api/v1/inventory") || uri.startsWith("/api/v1/suppliers")
                || uri.startsWith("/api/v1/purchase-orders") || uri.startsWith("/api/v1/transfers")
                || uri.startsWith("/api/v1/gift-cards")) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/problem+json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này");
            pd.setType(URI.create("about:blank"));
            pd.setTitle("Forbidden");
            pd.setInstance(URI.create(uri));

            List<String> perms = List.of();
            if (uri.startsWith("/api/v1/products") || uri.startsWith("/api/v1/collections")) {
                perms = List.of("MANAGE_PRODUCTS");
            } else if (uri.startsWith("/api/v1/inventory") || uri.startsWith("/api/v1/suppliers")
                    || uri.startsWith("/api/v1/purchase-orders") || uri.startsWith("/api/v1/transfers")) {
                perms = List.of("MANAGE_INVENTORY");
            } else if (uri.startsWith("/api/v1/gift-cards")) {
                perms = List.of("MANAGE_GIFT_CARDS");
            }
            if (!perms.isEmpty()) {
                pd.setProperty("requiredPermissions", perms);
            }
            objectMapper.writeValue(response.getWriter(), pd);
            return;
        }

        errorResponseWriter.write(request, response, ErrorCode.FORBIDDEN, null);
    }
}
