package com.thupo.managerShop.auth.ratelimit;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitFallbackTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisRateLimitStore redisRateLimitStore;
    private AppSecurityProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AppSecurityProperties();
        properties.getRateLimit().setFailOpen(true);

        redisRateLimitStore = new RedisRateLimitStore(redisTemplate, properties);
    }

    @Test
    @DisplayName("Khi Redis bị lỗi kết nối -> tự động Fallback sang In-Memory rate limit thay vì cho qua tự do")
    void shouldFallbackToInMemoryWhenRedisFails() {
        // Giả lập Redis bị sập / timeout
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new QueryTimeoutException("Redis connection timeout"));

        String clientIp = "192.168.1.100";
        int limit = 2;
        Duration window = Duration.ofMinutes(1);

        // Lần 1: Được phép (In-Memory count = 1)
        assertTrue(redisRateLimitStore.tryAcquire("login:ip:" + clientIp, limit, window));
        // Lần 2: Được phép (In-Memory count = 2)
        assertTrue(redisRateLimitStore.tryAcquire("login:ip:" + clientIp, limit, window));
        // Lần 3: BỊ CHẶN (In-Memory count = 3 > limit 2) -> Bảo vệ CPU không bị DoS dù Redis chết!
        assertFalse(redisRateLimitStore.tryAcquire("login:ip:" + clientIp, limit, window));
    }
}
