package com.thupo.managerShop.auth.ratelimit;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Triển khai RateLimitStore sử dụng Redis cho môi trường nhiều instances.
 * Đảm bảo tính nhất quán của bộ đếm giữa các nodes.
 * Hỗ trợ fail-open để tránh từ chối nhầm người dùng khi Redis gặp sự cố tạm thời.
 */
@Component
@ConditionalOnProperty(name = "app.security.rate-limit.store-type", havingValue = "redis", matchIfMissing = true)
public class RedisRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);

    private static final String PREFIX_RATE_LIMIT = "auth:ratelimit:";
    private static final String PREFIX_FAIL_COUNT = "auth:ip:fail_count:";
    private static final String PREFIX_FAIL_USERS = "auth:ip:fail_users:";

    private final StringRedisTemplate redisTemplate;
    private final boolean failOpen;

    public RedisRateLimitStore(StringRedisTemplate redisTemplate, AppSecurityProperties properties) {
        this.redisTemplate = redisTemplate;
        this.failOpen = properties.getRateLimit().isFailOpen();
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        String redisKey = PREFIX_RATE_LIMIT + key;
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                redisTemplate.expire(redisKey, window);
            }
            return count != null && count <= limit;
        } catch (DataAccessException e) {
            log.warn("Lỗi kết nối Redis khi kiểm tra rate limit cho khóa [{}]: {}", redisKey, e.getMessage());
            return failOpen;
        }
    }

    @Override
    public void recordIpFailure(String ip, String username, Duration window) {
        String countKey = PREFIX_FAIL_COUNT + ip;
        String usersKey = PREFIX_FAIL_USERS + ip;
        try {
            Long currentCount = redisTemplate.opsForValue().increment(countKey);
            if (currentCount != null && currentCount == 1) {
                redisTemplate.expire(countKey, window);
            }

            if (username != null && !username.isBlank()) {
                redisTemplate.opsForSet().add(usersKey, username.trim().toLowerCase());
                redisTemplate.expire(usersKey, window);
            }
        } catch (DataAccessException e) {
            log.warn("Lỗi kết nối Redis khi ghi nhận IP thất bại [{}]: {}", ip, e.getMessage());
        }
    }

    @Override
    public long getIpFailureCount(String ip, Duration window) {
        String countKey = PREFIX_FAIL_COUNT + ip;
        try {
            String val = redisTemplate.opsForValue().get(countKey);
            return val != null ? Long.parseLong(val) : 0L;
        } catch (DataAccessException | NumberFormatException e) {
            log.warn("Lỗi kết nối Redis khi đọc số lần sai của IP [{}]: {}", ip, e.getMessage());
            return 0L;
        }
    }

    @Override
    public long getIpDistinctUsernameCount(String ip, Duration window) {
        String usersKey = PREFIX_FAIL_USERS + ip;
        try {
            Long size = redisTemplate.opsForSet().size(usersKey);
            return size != null ? size : 0L;
        } catch (DataAccessException e) {
            log.warn("Lỗi kết nối Redis khi đếm số username của IP [{}]: {}", ip, e.getMessage());
            return 0L;
        }
    }

    @Override
    public void resetIp(String ip) {
        String countKey = PREFIX_FAIL_COUNT + ip;
        String usersKey = PREFIX_FAIL_USERS + ip;
        try {
            redisTemplate.delete(countKey);
            redisTemplate.delete(usersKey);
        } catch (DataAccessException e) {
            log.warn("Lỗi kết nối Redis khi reset bộ đếm IP [{}]: {}", ip, e.getMessage());
        }
    }
}
