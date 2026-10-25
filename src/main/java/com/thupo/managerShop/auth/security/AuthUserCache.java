package com.thupo.managerShop.auth.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.entity.UserStatus;
import com.thupo.managerShop.auth.repository.UserRepository;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Cache trạng thái user (còn dùng được không, token_version hiện tại) để {@link JwtAuthenticationFilter}
 * không phải query DB trên mỗi request.
 *
 * <p>Cache nằm trong bộ nhớ từng instance với TTL ngắn (mặc định 30 giây).
 * Khi tích hợp cùng {@link UserCacheBroadcaster} (Redis Pub/Sub), lệnh evict sẽ được đồng bộ ngay lập tức
 * tới toàn bộ các node khác trong cụm microservice/multi-instance.
 */
@Component
public class AuthUserCache {

    /** Trạng thái đã rút gọn của user, đủ để quyết định có chấp nhận access token hay không. */
    public record UserState(boolean usable, int tokenVersion) {
        static final UserState UNUSABLE = new UserState(false, -1);
    }

    private static final long MAX_ENTRIES = 50_000;

    private final UserRepository userRepository;
    private final Cache<Long, UserState> cache;
    private final ObjectProvider<UserCacheBroadcaster> broadcasterProvider;

    public AuthUserCache(UserRepository userRepository, AppSecurityProperties properties) {
        this(userRepository, properties, null);
    }

    @Autowired
    public AuthUserCache(UserRepository userRepository,
                         AppSecurityProperties properties,
                         ObjectProvider<UserCacheBroadcaster> broadcasterProvider) {
        this.userRepository = userRepository;
        this.broadcasterProvider = broadcasterProvider;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(properties.getJwt().getUserStateCacheSeconds()))
                .maximumSize(MAX_ENTRIES)
                .build();
    }

    public UserState get(Long userId) {
        return cache.get(userId, this::load);
    }

    /** Xoá cache trên node hiện tại và phát tín hiệu xoá cache trên toàn bộ cluster qua Redis Pub/Sub nếu có broadcaster. */
    public void evict(Long userId) {
        evictLocal(userId);
        if (broadcasterProvider != null) {
            broadcasterProvider.ifAvailable(b -> b.broadcastEvict(userId));
        }
    }

    /** Chỉ xoá cache trên node cục bộ (được gọi bởi Redis Pub/Sub listener khi nhận event từ node khác). */
    public void evictLocal(Long userId) {
        cache.invalidate(userId);
    }

    private UserState load(Long userId) {
        return userRepository.findAuthStateById(userId)
                .map(state -> new UserState(
                        state.getDeletedAt() == null && state.getStatus() == UserStatus.ACTIVE,
                        state.getTokenVersion()))
                .orElse(UserState.UNUSABLE);
    }
}
