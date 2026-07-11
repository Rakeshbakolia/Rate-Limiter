package com.example.ratelimiter.service;

import com.example.ratelimiter.model.RateLimitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class RateLimiterService {

    private static final int DEFAULT_CAPACITY = 10;
    private static final int DEFAULT_REFILL_RATE = 2;
    private static final String LOCKOUT_TIERS = "300,600,900,86400";
    private static final long STRIKE_FORGIVENESS_TTL = 172800L;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    @SuppressWarnings("rawtypes")
    private DefaultRedisScript<List> rateLimitScript;

    /**
     * Checks if the client request is allowed under the rate limit.
     * Evaluates both token bucket and progressive lockout state atomically in Redis.
     * 
     * @param clientId the unique identifier of the client
     * @return RateLimitResult indicating if allowed, remaining tokens, and potential wait time
     */
    public RateLimitResult checkRateLimit(String clientId) {
        String bucketKey = "ratelimit:bucket:" + clientId;
        String strikeKey = "ratelimit:strikes:" + clientId;
        List<String> keys = List.of(bucketKey, strikeKey);

        long currentTimeSeconds = Instant.now().getEpochSecond();

        try {
            // Execute the Lua script atomically
            List<?> result = redisTemplate.execute(
                    rateLimitScript,
                    keys,
                    String.valueOf(DEFAULT_CAPACITY),
                    String.valueOf(DEFAULT_REFILL_RATE),
                    String.valueOf(currentTimeSeconds),
                    String.valueOf(1), // requested tokens
                    LOCKOUT_TIERS,
                    String.valueOf(STRIKE_FORGIVENESS_TTL)
            );

            if (result != null && result.size() >= 3) {
                long isAllowed = ((Number) result.get(0)).longValue();
                int remaining = ((Number) result.get(1)).intValue();
                long penalty = ((Number) result.get(2)).longValue();

                return new RateLimitResult(isAllowed == 1, remaining, penalty);
            }

            // Fallback if result structure is unexpected
            log.warn("[RateLimiter] Unexpected response structure from Redis script: {}", result);
            return new RateLimitResult(true, 1, 0L);

        } catch (Exception e) {
            // Fail-open: log a severe warning and allow traffic
            log.error("[RateLimiter] Redis is down or timing out. Failing open to prevent outage. Details: {}", e.getMessage());
            return new RateLimitResult(true, 1, 0L);
        }
    }

    /**
     * Clears the lockout penalty count for the specified client.
     * Deletes the strikes key from Redis.
     * 
     * @param clientId the unique identifier of the client
     */
    public void clearLockout(String clientId) {
        String bucketKey = "ratelimit:bucket:" + clientId;
        String strikeKey = "ratelimit:strikes:" + clientId;
        redisTemplate.delete(List.of(bucketKey, strikeKey));
        log.info("[RateLimiter] Cleared strikes and token bucket for clientId: {}", clientId);
    }
}
