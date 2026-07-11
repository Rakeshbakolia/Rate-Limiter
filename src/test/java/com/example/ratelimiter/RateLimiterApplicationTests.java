package com.example.ratelimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.data.redis.cluster.nodes=",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.data.redis.timeout=50ms"
})
@AutoConfigureMockMvc
class RateLimiterApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String clientId;

    @BeforeEach
    void setUp() {
        clientId = "test-client-" + UUID.randomUUID().toString();
    }

    @Test
    void testRateLimiterFlowAndAdminReset() throws Exception {
        // 1. Send 10 successful requests (default capacity is 10)
        for (int i = 9; i >= 0; i--) {
            mockMvc.perform(get("/api/v1/resource")
                            .header("X-Client-ID", clientId))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(i)))
                    .andExpect(content().string("Success!"));
        }

        // 2. 11th request must fail with HTTP 429 and Retry-After = 300 (first lockout tier = 5 mins)
        mockMvc.perform(get("/api/v1/resource")
                        .header("X-Client-ID", clientId))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "300"))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded."))
                .andExpect(jsonPath("$.retry_after_seconds").value(300));

        // 3. Immediately retry -> should still fail, and waitTimeSeconds should be <= 300
        mockMvc.perform(get("/api/v1/resource")
                        .header("X-Client-ID", clientId))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded."));

        // 4. Reset lockout using Admin API
        mockMvc.perform(delete("/api/v1/admin/ratelimit/" + clientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value(containsString(clientId)));

        // 5. Send request again -> should succeed because lockout and bucket were cleared
        mockMvc.perform(get("/api/v1/resource")
                        .header("X-Client-ID", clientId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "9"))
                .andExpect(content().string("Success!"));
    }
}
