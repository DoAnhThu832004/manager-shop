package com.thupo.managerShop.auth.ratelimit;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
import java.time.Duration;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Triển khai RateLimitStore sử dụng Redis cho môi trường nhiều instances.
 * Đảm bảo tính nhất quán của bộ đếm giữa các nodes.
 *
 * <p>Kiến trúc phòng thủ có khả năng phục hồi cao (Resilient Degradation):
 * Thay vì Fail-Open hoàn toàn (cho qua mù quáng gây cạn kiệt CPU do BCrypt khi Redis sập),
 * hệ thống tự động Fallback sang {@link MySqlRateLimitStore} (In-Memory Caffeine) trên RAM của từng node
 * để tiếp tục khống chế lưu lượng, bảo vệ máy chủ Tomcat và MySQL.
 */
@Component
@ConditionalOnProperty(name = "app.security.rate-limit.store-type", havingValue = "redis", matchIfMissing = true)
public class RedisRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);

    private static final String PREFIX_RATE_LIMIT = "auth:ratelimit:";
    private static final String PREFIX_FAIL_COUNT = "auth:ip:fail_count:";
    private static final String PREFIX_FAIL_USERS = "auth:ip:fail_users:";

    private static final String LUA_RATE_LIMIT_SCRIPT =
            "local current = redis.call('INCR', KEYS[1])\n" +
            "if current == 1 then\n" +
            "    redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "return current";

    private final StringRedisTemplate redisTemplate;
    private final boolean failOpen;
    private final MySqlRateLimitStore inMemoryFallback;
    private final RedisScript<Long> rateLimitScript;

    public RedisRateLimitStore(StringRedisTemplate redisTemplate, AppSecurityProperties properties) {
        this.redisTemplate = redisTemplate;
        this.failOpen = properties.getRateLimit().isFailOpen();
        this.inMemoryFallback = new MySqlRateLimitStore();
        this.rateLimitScript = new DefaultRedisScript<>(LUA_RATE_LIMIT_SCRIPT, Long.class);
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        String redisKey = PREFIX_RATE_LIMIT + key;
        try {
            Long count = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(Math.max(1, window.toSeconds()))
            );
            return count != null && count <= limit;
        } catch (DataAccessException e) {
            log.warn("Lỗi kết nối Redis khi kiểm tra rate limit cho khóa [{}]: {}. Kích hoạt Fallback In-Memory Rate Limiter.",
                    redisKey, e.getMessage());
            if (failOpen) {
                return inMemoryFallback.tryAcquire(key, limit, window);
            }
            return false;
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
            log.warn("Lỗi kết nối Redis khi ghi nhận IP thất bại [{}]: {}. Lưu tạm vào In-Memory fallback.", ip, e.getMessage());
            inMemoryFallback.recordIpFailure(ip, username, window);
        }
    }

    @Override
    public long getIpFailureCount(String ip, Duration window) {
        String countKey = PREFIX_FAIL_COUNT + ip;
        try {
            String val = redisTemplate.opsForValue().get(countKey);
            return val != null ? Long.parseLong(val) : 0L;
        } catch (DataAccessException | NumberFormatException e) {
            log.warn("Lỗi kết nối Redis khi đọc số lần sai của IP [{}]: {}. Đọc từ In-Memory fallback.", ip, e.getMessage());
            return inMemoryFallback.getIpFailureCount(ip, window);
        }
    }

    @Override
    public long getIpDistinctUsernameCount(String ip, Duration window) {
        String usersKey = PREFIX_FAIL_USERS + ip;
        try {
            Long size = redisTemplate.opsForSet().size(usersKey);
            return size != null ? size : 0L;
        } catch (DataAccessException e) {
            log.warn("Lỗi kết nối Redis khi đếm số username của IP [{}]: {}. Đọc từ In-Memory fallback.", ip, e.getMessage());
            return inMemoryFallback.getIpDistinctUsernameCount(ip, window);
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
            log.warn("Lỗi kết nối Redis khi reset bộ đếm IP [{}]: {}. Xoá trên In-Memory fallback.", ip, e.getMessage());
            inMemoryFallback.resetIp(ip);
        }
    }
}
