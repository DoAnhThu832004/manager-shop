package com.thupo.managerShop.auth.security;

import com.thupo.managerShop.auth.exception.ErrorCode;
import com.thupo.managerShop.auth.exception.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Chặn IP ngay từ đầu, đăng ký TRƯỚC Spring Security filter chain (xem {@code SecurityConfig}).
 * Request từ IP bị chặn trả 429 ngay, không chạm DB user hay BCrypt để tiết kiệm CPU.
 * Đồng thời gắn IP thật vào request attribute để các tầng sau dùng lại, không phải resolve lần nữa.
 */
public class IpBlockFilter extends OncePerRequestFilter {

    public static final String CLIENT_IP_ATTRIBUTE = "auth.clientIp";

    private final ClientIpResolver clientIpResolver;
    private final IpAccessPolicy ipAccessPolicy;
    private final ErrorResponseWriter errorResponseWriter;

    public IpBlockFilter(ClientIpResolver clientIpResolver, IpAccessPolicy ipAccessPolicy,
                         ErrorResponseWriter errorResponseWriter) {
        this.clientIpResolver = clientIpResolver;
        this.ipAccessPolicy = ipAccessPolicy;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = clientIpResolver.resolve(request);
        request.setAttribute(CLIENT_IP_ATTRIBUTE, clientIp);

        if (ipAccessPolicy.isBlocked(clientIp)) {
            errorResponseWriter.write(request, response, ErrorCode.AUTH_IP_BLOCKED,
                    ipAccessPolicy.retryAfterSeconds(clientIp));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
