package com.thupo.managerShop.auth.security;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        AppSecurityProperties properties = new AppSecurityProperties();
        // Cấu hình proxy tin cậy: dải 10.0.0.0/8 và proxy IP cụ thể 192.168.1.100
        properties.setTrustedProxies(List.of("10.0.0.0/8", "192.168.1.100"));
        resolver = new ClientIpResolver(properties);
    }

    @Test
    @DisplayName("Request trực tiếp không qua proxy: lấy đúng remoteAddr")
    void shouldReturnRemoteAddrWhenNoProxy() {
        String result = resolver.resolve("203.0.113.195", null);
        assertEquals("203.0.113.195", result);
    }

    @Test
    @DisplayName("Request qua proxy tin cậy: lấy đúng IP client từ X-Forwarded-For")
    void shouldResolveClientIpFromTrustedProxy() {
        // remoteAddr là proxy tin cậy (10.0.0.1 thuộc dải 10.0.0.0/8)
        String remoteAddr = "10.0.0.1";
        String forwardedFor = "203.0.113.50";

        String result = resolver.resolve(remoteAddr, forwardedFor);
        assertEquals("203.0.113.50", result);
    }

    @Test
    @DisplayName("Request qua chuỗi proxy: duyệt từ phải sang trái để lấy IP ngoài cùng tin cậy")
    void shouldResolveClientIpAcrossMultipleProxies() {
        // Client thật: 198.51.100.22, qua proxy nội bộ 10.1.2.3, tới ingress 10.0.0.1
        String remoteAddr = "10.0.0.1";
        String forwardedFor = "198.51.100.22, 10.1.2.3";

        String result = resolver.resolve(remoteAddr, forwardedFor);
        assertEquals("198.51.100.22", result);
    }

    @Test
    @DisplayName("Kẻ tấn công gửi header X-Forwarded-For giả mạo trực tiếp (remoteAddr không phải proxy tin cậy)")
    void shouldIgnoreForwardedHeaderFromUntrustedSource() {
        // IP nguồn lạ (không thuộc trusted proxies) tự đính kèm header X-Forwarded-For
        String remoteAddr = "198.51.100.77";
        String forwardedFor = "8.8.8.8, 1.1.1.1";

        String result = resolver.resolve(remoteAddr, forwardedFor);
        // Phải trả về remoteAddr thật, không được tin header giả mạo
        assertEquals("198.51.100.77", result);
    }

    @Test
    @DisplayName("Header X-Forwarded-For chứa giá trị không hợp lệ (không phải IP literal)")
    void shouldFallbackToRemoteAddrWhenHeaderMalformed() {
        String remoteAddr = "10.0.0.1";
        String forwardedFor = "invalid-ip-string, 10.0.0.2";

        String result = resolver.resolve(remoteAddr, forwardedFor);
        assertEquals("10.0.0.1", result);
    }
}
