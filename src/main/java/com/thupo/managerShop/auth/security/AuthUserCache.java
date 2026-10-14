package com.thupo.managerShop.auth.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.entity.UserStatus;
import com.thupo.managerShop.auth.repository.UserRepository;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Cache trạng thái user (còn dùng được không, token_version hiện tại) để {@link JwtAuthenticationFilter}
 * không phải query DB trên mỗi request.
 *
 * <p>Cache nằm trong bộ nhớ từng instance với TTL ngắn (mặc định 30 giây): khi user bị vô hiệu hoá hoặc
 * tăng token_version, mọi instance sẽ từ chối access token cũ chậm nhất sau TTL. {@link #evict(Long)} chỉ
 * có tác dụng trên instance hiện tại.
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

    public AuthUserCache(UserRepository userRepository, AppSecurityProperties properties) {
        this.userRepository = userRepository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(properties.getJwt().getUserStateCacheSeconds()))
                .maximumSize(MAX_ENTRIES)
                .build();
    }

    public UserState get(Long userId) {
        return cache.get(userId, this::load);
    }

    public void evict(Long userId) {
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
