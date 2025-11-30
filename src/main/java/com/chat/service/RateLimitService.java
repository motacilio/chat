package com.chat.service;

import com.chat.exception.RateLimitExceededException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Rate Limiting Service using Resilience4j
 * 
 * Responsibility: Enforces rate limits per user using Resilience4j rate limiter to prevent abuse.
 * Does NOT: Handle authentication (userId assumed valid), implement business logic.
 * 
 * Distributed Systems Concept: Rate limiting ensures fair resource allocation in multi-tenant
 * systems and prevents single users from exhausting system resources.
 * 
 * Usage Example:
 * <pre>
 * // In ChatServiceImpl
 * rateLimitService.checkMessageSendingLimit(userId);  // Throws if rate limited
 * </pre>
 * 
 * Rate Limits (configured in application.yml):
 * - Message sending: 100 messages per minute per user
 * - File uploads: 10 uploads per minute per user
 * 
 * Implementation Notes:
 * - Uses Resilience4j RateLimiter (simpler than Bucket4j)
 * - In-memory tracking (not distributed across API instances)
 * - For 3 instances: effective limit is 3 × 100 = 300 msg/min per user across all instances
 * - For distributed rate limiting, migrate to Redis (future enhancement)
 */
@Slf4j
@Service
public class RateLimitService {

    private final RateLimiterRegistry rateLimiterRegistry;

    public RateLimitService(RateLimiterRegistry rateLimiterRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    /**
     * Checks if user is allowed to send a message based on rate limit.
     * Throws exception if limit exceeded.
     * 
     * @param userId User ID (from JWT authentication)
     * @throws RateLimitExceededException if rate limit exceeded
     */
    public void checkMessageSendingLimit(String userId) {
        RateLimiter limiter = rateLimiterRegistry.rateLimiter("sendMessage-" + userId, "sendMessage");
        
        boolean allowed = limiter.acquirePermission();
        
        if (!allowed) {
            log.warn("Rate limit exceeded for user {} on message sending (100 msg/min)", userId);
            throw new RateLimitExceededException(userId, "sendMessage", 60);
        }
        
        log.debug("Rate limit check passed for user {}", userId);
    }

    /**
     * Checks if user is allowed to upload a file based on rate limit.
     * Throws exception if limit exceeded.
     * 
     * @param userId User ID (from JWT authentication)
     * @throws RateLimitExceededException if rate limit exceeded
     */
    public void checkFileUploadLimit(String userId) {
        RateLimiter limiter = rateLimiterRegistry.rateLimiter("fileUpload-" + userId, "fileUpload");
        
        boolean allowed = limiter.acquirePermission();
        
        if (!allowed) {
            log.warn("Rate limit exceeded for user {} on file upload (10 uploads/min)", userId);
            throw new RateLimitExceededException(userId, "fileUpload", 60);
        }
        
        log.debug("Rate limit check passed for user {}", userId);
    }
}
