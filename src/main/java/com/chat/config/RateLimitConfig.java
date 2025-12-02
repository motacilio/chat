package com.chat.config;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Rate Limiting Configuration with Resilience4j
 * 
 * Responsibility: Configures rate limiting using Resilience4j to prevent abuse
 * and ensure fair usage across users.
 * Does NOT: Handle authentication (see SecurityConfig), manage business logic (see services).
 * 
 * Distributed Systems Concept: Rate limiting prevents resource exhaustion and ensures fair resource
 * allocation in multi-tenant systems. Resilience4j provides lightweight, annotation-based rate limiting.
 * 
 * Scalability Configuration:
 * - Per-user rate limits: Each user_id gets independent rate limit instance
 * - Message limit: 100 messages per minute (Docker profile)
 * - File upload limit: 10 uploads per minute (Docker profile)
 * - Timeout: 0 seconds (reject immediately if limit exceeded)
 * 
 * IMPORTANT: Manual configuration registration required due to Spring Boot 3.x property binding issues
 * with Resilience4j nested properties. Environment variables and application-*.yml properties
 * are not being loaded into RateLimiterRegistry instances.
 * 
 * This configuration registers Rate Limiter configurations (not beans) in the RateLimiterRegistry
 * so that RateLimitService can create per-user limiters using:
 * rateLimiterRegistry.rateLimiter("sendMessage-{userId}", "sendMessage")
 * 
 * Trade-offs:
 * - In-memory tracking doesn't share state across API instances
 * - For distributed rate limiting across instances, use Redis (future enhancement)
 * - Current design: 3 API instances × 100 msg/min = 300 msg/min total per user (acceptable for MVP)
 */
@Component
public class RateLimitConfig {

    private final RateLimiterRegistry registry;

    public RateLimitConfig(RateLimiterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Registers Rate Limiter configurations in the registry after bean construction.
     * These configurations are used by RateLimitService to create per-user limiter instances.
     */
    @PostConstruct
    public void registerConfigurations() {
        // Register sendMessage configuration (100 msg/min)
        RateLimiterConfig sendMessageConfig = RateLimiterConfig.custom()
            .limitForPeriod(100)
            .limitRefreshPeriod(Duration.ofSeconds(60))
            .timeoutDuration(Duration.ZERO)
            .build();
        
        registry.addConfiguration("sendMessage", sendMessageConfig);

        // Register fileUpload configuration (10 uploads/min)
        RateLimiterConfig fileUploadConfig = RateLimiterConfig.custom()
            .limitForPeriod(10)
            .limitRefreshPeriod(Duration.ofSeconds(60))
            .timeoutDuration(Duration.ZERO)
            .build();
        
        registry.addConfiguration("fileUpload", fileUploadConfig);
    }
}
