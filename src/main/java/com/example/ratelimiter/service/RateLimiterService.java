package com.example.ratelimiter.service;

import com.example.ratelimiter.metrics.RateLimiterMetrics;
import com.example.ratelimiter.model.RateLimitResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
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

    @Autowired
    private RateLimiterMetrics metrics;

    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * Checks if the client request is allowed under the rate limit.
     * Evaluates both token bucket and progressive lockout state atomically in Redis.
     * Uses Java 21 Virtual Thread compatibility (no thread pinning monitor locks).
     * 
     * @param clientId the unique identifier of the client
     * @return RateLimitResult indicating if allowed, remaining tokens, and potential wait time
     */
    public RateLimitResult checkRateLimit(String clientId) {
        // Redis Cluster slot key hashtag constraint: both keys MUST map to the same slot.
        // We wrap the clientId in curly braces {} to force Redis to hash only the clientId,
        // placing both keys on the same physical cluster node.
        String bucketKey = "ratelimit:bucket:{" + clientId + "}";
        String strikeKey = "ratelimit:strikes:{" + clientId + "}";
        List<String> keys = List.of(bucketKey, strikeKey);

        long currentTimeSeconds = Instant.now().getEpochSecond();
        Timer.Sample sample = Timer.start(meterRegistry);

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

                boolean allowed = isAllowed == 1;
                boolean wasPenalty = penalty > 0;

                metrics.recordRequest(allowed, wasPenalty);
                return new RateLimitResult(allowed, remaining, penalty);
            }

            // Fallback if result structure is unexpected
            log.warn("[RateLimiter] Unexpected response structure from Redis script: {}", result);
            metrics.recordRequest(true, false);
            return new RateLimitResult(true, 1, 0L);

        } catch (QueryTimeoutException | RedisConnectionFailureException e) {
            log.error("[RateLimiter] Redis cluster timeout/failure. Triggering Fail-Open. Details: {}", e.getMessage());
            metrics.recordFailOpen();
            return new RateLimitResult(true, 1, 0L);
        } catch (Exception e) {
            // Catch-all to guarantee robust fail-open safety for any other database error
            log.error("[RateLimiter] General Redis/System failure. Triggering Fail-Open. Details: {}", e.getMessage());
            metrics.recordFailOpen();
            return new RateLimitResult(true, 1, 0L);
        } finally {
            sample.stop(meterRegistry.timer("ratelimit.redis.latency"));
        }
    }

    /**
     * Clears the lockout penalty count for the specified client.
     * Deletes the strikes key from Redis.
     * 
     * @param clientId the unique identifier of the client
     */
    public void clearLockout(String clientId) {
        String bucketKey = "ratelimit:bucket:{" + clientId + "}";
        String strikeKey = "ratelimit:strikes:{" + clientId + "}";
        redisTemplate.delete(List.of(bucketKey, strikeKey));
        log.info("[RateLimiter] Cleared strikes and token bucket for clientId: {}", clientId);
    }
}
