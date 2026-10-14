package com.thupo.managerShop.auth.security;

import com.thupo.managerShop.auth.exception.ErrorCode;
import com.thupo.managerShop.auth.exception.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Trả 401 dạng JSON thống nhất, ưu tiên mã lỗi cụ thể do {@link JwtAuthenticationFilter} ghi lại. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter errorResponseWriter;

    public RestAuthenticationEntryPoint(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object attribute = request.getAttribute(JwtAuthenticationFilter.ERROR_ATTRIBUTE);
        ErrorCode code = attribute instanceof ErrorCode errorCode ? errorCode : ErrorCode.AUTH_UNAUTHORIZED;
        errorResponseWriter.write(request, response, code, null);
    }
}
