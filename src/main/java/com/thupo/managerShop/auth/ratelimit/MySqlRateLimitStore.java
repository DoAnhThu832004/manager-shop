package com.thupo.managerShop.auth.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Triển khai RateLimitStore bằng bộ nhớ In-Memory/Caffeine dự phòng khi không có Redis.
 * <p>
 * Đánh giá Trade-off:
 * - Khi nào dùng: Chạy 1 instance đơn lẻ, môi trường Local Development, hoặc Unit/Integration Test.
 * - Hạn chế: Khi chạy nhiều instances (cluster), mỗi instance đếm riêng rẽ dẫn đến ngưỡng bị lệch
 *   và mất toàn bộ bộ đếm khi ứng dụng khởi động lại.
 */
@Component
@ConditionalOnProperty(name = "app.security.rate-limit.store-type", havingValue = "mysql")
public class MySqlRateLimitStore implements RateLimitStore {

    private final Cache<String, AtomicInteger> rateLimitCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(50_000)
            .build();

    private final Cache<String, AtomicInteger> ipFailCountCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(50_000)
            .build();

    private final Cache<String, Set<String>> ipFailUsersCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(50_000)
            .build();

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        AtomicInteger counter = rateLimitCache.get(key, k -> new AtomicInteger(0));
        return counter != null && counter.incrementAndGet() <= limit;
    }

    @Override
    public void recordIpFailure(String ip, String username, Duration window) {
        AtomicInteger counter = ipFailCountCache.get(ip, k -> new AtomicInteger(0));
        if (counter != null) {
            counter.incrementAndGet();
        }

        if (username != null && !username.isBlank()) {
            Set<String> users = ipFailUsersCache.get(ip, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
            if (users != null) {
                users.add(username.trim().toLowerCase());
            }
        }
    }

    @Override
    public long getIpFailureCount(String ip, Duration window) {
        AtomicInteger counter = ipFailCountCache.getIfPresent(ip);
        return counter != null ? counter.get() : 0L;
    }

    @Override
    public long getIpDistinctUsernameCount(String ip, Duration window) {
        Set<String> users = ipFailUsersCache.getIfPresent(ip);
        return users != null ? users.size() : 0L;
    }

    @Override
    public void resetIp(String ip) {
        ipFailCountCache.invalidate(ip);
        ipFailUsersCache.invalidate(ip);
    }
}
