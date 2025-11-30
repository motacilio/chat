package com.chat.exception;

/**
 * Exception thrown when user exceeds rate limit.
 * 
 * Responsibility: Signals that user has exceeded allowed rate (e.g., 100 messages/minute).
 * Does NOT: Handle retry logic (client responsibility).
 * 
 * Distributed Systems Concept: Rate limiting exceptions provide backpressure signal to clients,
 * preventing system overload. Clients should implement exponential backoff when receiving this error.
 * 
 * gRPC Mapping: Maps to RESOURCE_EXHAUSTED status code (HTTP 429 Too Many Requests equivalent)
 */
public class RateLimitExceededException extends RuntimeException {
    
    private final String userId;
    private final String operation;
    private final long retryAfterSeconds;

    public RateLimitExceededException(String userId, String operation, long retryAfterSeconds) {
        super(String.format("Rate limit exceeded for user %s on operation '%s'. Retry after %d seconds.", 
            userId, operation, retryAfterSeconds));
        this.userId = userId;
        this.operation = operation;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitExceededException(String userId, String operation) {
        this(userId, operation, 60);  // Default: retry after 1 minute
    }

    public String getUserId() {
        return userId;
    }

    public String getOperation() {
        return operation;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
