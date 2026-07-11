package com.example.ratelimiter;

import com.example.ratelimiter.model.RateLimitResult;
import com.example.ratelimiter.service.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(properties = {
    "spring.data.redis.cluster.nodes=",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.data.redis.timeout=50ms"
})
class RateLimiterResilienceTests {

    @Autowired
    private RateLimiterService rateLimiterService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    /**
     * Verifies that if Redis throws a connection failure or command timeout exception,
     * the system fails open gracefully, allows the request, and logs the issue.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testFailOpenWhenRedisThrowsException() {
        // Mock RedisTemplate to throw connection failure exception when executing the rate limit script
        Mockito.when(redisTemplate.execute(
                any(RedisScript.class),
                any(List.class),
                any(Object[].class)
        )).thenThrow(new RedisConnectionFailureException("Redis connection refused"));

        // Evaluate client status
        RateLimitResult result = rateLimiterService.checkRateLimit("resilient-client");

        // Verify result is allowed (fail-open)
        assertTrue(result.allowed());
        assertEquals(1, result.remainingTokens());
        assertEquals(0L, result.waitTimeSeconds());
    }
}
