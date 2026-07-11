package com.example.ratelimiter.model;

public record RateLimitResult(boolean allowed, int remainingTokens, long waitTimeSeconds) {}
