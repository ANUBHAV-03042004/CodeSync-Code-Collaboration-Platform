package com.authservice.codesync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis configuration for the auth-service.
 *
 * Used exclusively by TokenBlacklistService for:
 *   - Token blacklisting on logout     (key: "blacklist:{token}")
 *   - Inactivity session tracking      (key: "activity:{userId}")
 *
 * Connection details are in application.properties under spring.data.redis.*
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
