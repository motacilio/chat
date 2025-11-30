package com.chat.config;

import org.springframework.context.annotation.Configuration;

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
 * - Message limit: 100 messages per minute (configured in application.yml)
 * - File upload limit: 10 uploads per minute (configured in application.yml)
 * - Timeout: 0 seconds (reject immediately if limit exceeded)
 * 
 * Configuration is in application.yml:
 * <pre>
 * resilience4j:
 *   ratelimiter:
 *     instances:
 *       sendMessage:
 *         limitForPeriod: 100
 *         limitRefreshPeriod: 60s
 *         timeoutDuration: 0s
 *       fileUpload:
 *         limitForPeriod: 10
 *         limitRefreshPeriod: 60s
 *         timeoutDuration: 0s
 * </pre>
 * 
 * Trade-offs:
 * - In-memory tracking doesn't share state across API instances
 * - For distributed rate limiting across instances, use Redis (future enhancement)
 * - Current design: 3 API instances × 100 msg/min = 300 msg/min total per user (acceptable for MVP)
 */
@Configuration
public class RateLimitConfig {
    // Configuration is handled by Resilience4j autoconfiguration from application.yml
    // No additional beans required
}
