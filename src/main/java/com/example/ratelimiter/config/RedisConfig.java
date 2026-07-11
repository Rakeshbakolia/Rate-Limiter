package com.example.ratelimiter.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import java.time.Duration;
import java.util.List;

@Configuration
public class RedisConfig {

    /**
     * Configures LettuceConnectionFactory supporting either a Redis Cluster or Standalone configuration.
     * 
     * @param redisProperties autowired properties from application.yml
     * @return LettuceConnectionFactory
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory(RedisProperties redisProperties) {
        // Configure common-pool2 settings
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        RedisProperties.Pool pool = redisProperties.getLettuce().getPool();
        if (pool != null) {
            poolConfig.setMaxTotal(pool.getMaxActive());
            poolConfig.setMaxIdle(pool.getMaxIdle());
            poolConfig.setMinIdle(pool.getMinIdle());
        }
        
        LettucePoolingClientConfiguration lettuceClientConfiguration = LettucePoolingClientConfiguration.builder()
                .commandTimeout(redisProperties.getTimeout() != null ? redisProperties.getTimeout() : Duration.ofMillis(50))
                .poolConfig(poolConfig)
                .build();

        // If cluster settings are provided in application.yml, build a Cluster Configuration
        if (redisProperties.getCluster() != null && redisProperties.getCluster().getNodes() != null && !redisProperties.getCluster().getNodes().isEmpty()) {
            RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(
                    redisProperties.getCluster().getNodes()
            );
            return new LettuceConnectionFactory(clusterConfig, lettuceClientConfiguration);
        } else {
            // Standalone configuration fallback (ideal for local development and integration tests)
            RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
            standaloneConfig.setHostName(redisProperties.getHost() != null ? redisProperties.getHost() : "localhost");
            standaloneConfig.setPort(redisProperties.getPort() != 0 ? redisProperties.getPort() : 6379);
            return new LettuceConnectionFactory(standaloneConfig, lettuceClientConfiguration);
        }
    }

    /**
     * Configures the StringRedisTemplate for Redis interactions using the custom cluster connection factory.
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
