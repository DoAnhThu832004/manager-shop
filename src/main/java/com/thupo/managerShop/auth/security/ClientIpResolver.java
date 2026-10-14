package com.thupo.managerShop.auth.security;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Xác định IP thật của client.
 *
 * <p>Chỉ đọc {@code X-Forwarded-For} khi request đến trực tiếp từ một proxy tin cậy ({@code trusted-proxies}).
 * Nếu không, kẻ tấn công có thể tự đặt header này để giả IP và né cơ chế chặn IP.
 * Header được duyệt từ phải sang trái: phần tử đầu tiên không thuộc proxy tin cậy là client thật, vì các
 * phần tử bên trái có thể do client tự thêm vào.
 */
@Component
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final List<String> trustedProxies;

    public ClientIpResolver(AppSecurityProperties properties) {
        this.trustedProxies = List.copyOf(properties.getTrustedProxies());
    }

    public String resolve(HttpServletRequest request) {
        return resolve(request.getRemoteAddr(), request.getHeader(X_FORWARDED_FOR));
    }

    /** Tách riêng để dễ unit test không cần dựng HttpServletRequest. */
    public String resolve(String remoteAddr, String forwardedFor) {
        String remote = CidrMatcher.normalize(remoteAddr);
        if (!isTrustedProxy(remote) || forwardedFor == null || forwardedFor.isBlank()) {
            return remote;
        }
        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String candidate = hops[i].trim();
            if (!CidrMatcher.isIpLiteral(candidate)) {
                // Header sai định dạng: không tin, dùng địa chỉ của proxy.
                return remote;
            }
            String normalized = CidrMatcher.normalize(candidate);
            if (!isTrustedProxy(normalized)) {
                return normalized;
            }
        }
        return remote;
    }

    private boolean isTrustedProxy(String ip) {
        for (String proxy : trustedProxies) {
            if (CidrMatcher.matches(proxy, ip)) {
                return true;
            }
        }
        return false;
    }
}
