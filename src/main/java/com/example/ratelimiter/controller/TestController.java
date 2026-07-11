package com.example.ratelimiter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    /**
     * Test endpoint to verify rate-limiting functionality.
     * Returns a plain string success message.
     */
    @GetMapping("/api/v1/resource")
    public String getResource() {
        return "Success!";
    }
}
