package com.thupo.managerShop.auth.security;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Đồng bộ hoá việc vô hiệu hoá User State Cache (token_version, status) giữa các node Spring Boot
 * thông qua Redis Pub/Sub.
 */
@Component
@ConditionalOnProperty(name = "app.security.rate-limit.store-type", havingValue = "redis", matchIfMissing = true)
public class RedisUserCacheBroadcaster implements UserCacheBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RedisUserCacheBroadcaster.class);
    public static final String TOPIC_USER_INVALIDATE = "auth:user:invalidate";

    private final StringRedisTemplate redisTemplate;

    public RedisUserCacheBroadcaster(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void broadcastEvict(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redisTemplate.convertAndSend(TOPIC_USER_INVALIDATE, String.valueOf(userId));
        } catch (Exception e) {
            log.warn("Không thể broadcast evict user [{}] qua Redis Pub/Sub: {}", userId, e.getMessage());
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "app.security.rate-limit.store-type", havingValue = "redis", matchIfMissing = true)
    static class RedisPubSubConfig {

        @Bean
        public RedisMessageListenerContainer redisMessageListenerContainer(
                RedisConnectionFactory connectionFactory,
                @Lazy AuthUserCache authUserCache) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.addMessageListener((message, pattern) -> {
                try {
                    String body = new String(message.getBody(), StandardCharsets.UTF_8);
                    Long userId = Long.parseLong(body.trim());
                    authUserCache.evictLocal(userId);
                } catch (Exception ignored) {
                }
            }, new ChannelTopic(TOPIC_USER_INVALIDATE));
            return container;
        }
    }
}
