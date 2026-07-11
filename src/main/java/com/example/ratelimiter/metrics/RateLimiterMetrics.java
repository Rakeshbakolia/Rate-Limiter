package com.example.ratelimiter.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RateLimiterMetrics {

    private final MeterRegistry meterRegistry;

    public RateLimiterMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Increments the request counter tagged with allowed/denied and bucket/penalty details.
     * 
     * @param allowed whether the request was allowed
     * @param wasPenalty whether the request evaluation failed due to a lockout penalty
     */
    public void recordRequest(boolean allowed, boolean wasPenalty) {
        String status = allowed ? "allowed" : "denied";
        String type = wasPenalty ? "penalty" : "bucket";
        meterRegistry.counter("ratelimit.requests.total", "status", status, "type", type).increment();
    }

    /**
     * Increments the counter tracking Redis cluster connection timeouts/failures
     * where the system falls open to maintain service availability.
     */
    public void recordFailOpen() {
        meterRegistry.counter("ratelimit.redis.failopen").increment();
    }
}
