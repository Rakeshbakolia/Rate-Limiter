package com.example.ratelimiter.filter;

import com.example.ratelimiter.model.RateLimitResult;
import com.example.ratelimiter.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private RateLimiterService rateLimiterService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/admin/") || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Extract client identifier from X-Client-ID header, fallback to remote IP address
        String clientId = request.getHeader("X-Client-ID");
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = request.getRemoteAddr();
        }

        RateLimitResult result = rateLimiterService.checkRateLimit(clientId);

        if (result.allowed()) {
            // Request is allowed: set response header and proceed with chain
            response.addHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            // Request is blocked: set HTTP 429 status, retry after header, and return error JSON
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.addHeader("Retry-After", String.valueOf(result.waitTimeSeconds()));

            String jsonResponse = String.format(
                    "{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded.\", \"retry_after_seconds\": %d}",
                    result.waitTimeSeconds()
            );
            response.getWriter().write(jsonResponse);
        }
    }
}
