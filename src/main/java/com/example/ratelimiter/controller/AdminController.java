package com.example.ratelimiter.controller;

import com.example.ratelimiter.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AdminController {

    @Autowired
    private RateLimiterService rateLimiterService;

    /**
     * Admin endpoint to manually clear a user's rate limiting penalty box (strikes).
     * 
     * @param clientId the unique identifier of the client to reset
     * @return response indicating success
     */
    @DeleteMapping("/api/v1/admin/ratelimit/{clientId}")
    public ResponseEntity<Map<String, String>> clearRateLimit(@PathVariable String clientId) {
        rateLimiterService.clearLockout(clientId);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Rate limit and lockout cleared for client: " + clientId
        ));
    }
}
