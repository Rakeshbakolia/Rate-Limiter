package com.example.ratelimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

@Configuration
public class RedisConfig {

    /**
     * Configures the StringRedisTemplate for Redis interactions.
     * Uses the default connection factory configured by Spring Boot (Lettuce).
     */
    @Bean
    public StringRedisTemplate redisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Loads the progressive lockout Lua script from the classpath resources.
     * Maps the return structure to java.util.List of integers/longs.
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public DefaultRedisScript<List> rateLimitScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/progressive_lockout.lua"));
        script.setResultType(List.class);
        return script;
    }
}
