package com.chat.adapter;

import com.chat.adapter.dto.ConnectionResult;
import com.chat.adapter.dto.PlatformCredentials;
import com.chat.adapter.dto.SendResult;
import com.chat.model.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Responsibility: Mock implementation of Instagram Direct Messages API adapter for educational/testing purposes.
 * Does NOT: Make real API calls, persist data, handle webhooks (see TelegramBotAdapter for real integration).
 * 
 * Distributed Systems Concept: Simulates realistic production behavior (90% success rate, 150-400ms latency,
 * common error scenarios) to enable testing of retry logic, circuit breakers, and partial failure handling
 * WITHOUT dependency on external Instagram Graph API (which requires Facebook Business verification).
 * 
 * Educational Value: Demonstrates mock implementation with DIFFERENT failure characteristics than WhatsApp
 * (lower success rate, higher latency) - teaches students that external services have varying SLAs and
 * system MUST handle heterogeneous reliability profiles.
 * 
 * Mock Behavior (per FR-039 and Session 2025-11-24 Q4):
 * - Success Rate: 90% (10% random failures - less reliable than WhatsApp)
 * - Latency: Random 150-400ms (higher than WhatsApp due to Graph API complexity)
 * - Error Types: connection_timeout (4%), rate_limit_exceeded (4%), invalid_recipient (2%)
 * - External ID Validation: @username pattern (@john_doe, @jane_smith)
 * 
 * Pattern: Mock Object - replaces external dependency with controlled simulation.
 */
@Component("instagramAdapter")
public class InstagramMockAdapter implements PlatformAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(InstagramMockAdapter.class);
    
    // Mock behavior configuration (per FR-039)
    private static final double SUCCESS_RATE = 0.90; // 90% success (lower than WhatsApp)
    private static final int MIN_LATENCY_MS = 150;
    private static final int MAX_LATENCY_MS = 400;
    
    // Error distribution (10% total failures - higher than WhatsApp)
    private static final double CONNECTION_TIMEOUT_RATE = 0.04; // 4%
    private static final double RATE_LIMIT_RATE = 0.04;         // 4%
    private static final double INVALID_RECIPIENT_RATE = 0.02;  // 2%
    
    // Instagram username validation (per Session 2025-11-24 Q5)
    // Pattern: @[alphanumeric_._] (e.g., @john_doe, @jane.smith, @user123)
    // Rules: starts with @, followed by 1-30 alphanumeric/underscore/period characters
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^@[a-zA-Z0-9._]{1,30}$");
    
    private final Random random = new Random();
    private boolean isConnected = false;
    
    /**
     * Simulates Instagram Graph API connection establishment.
     * 
     * Educational Note: Real Instagram API requires OAuth 2.0 authentication with access token
     * obtained through Facebook Business login flow. Mock always succeeds (connection is local state).
     * 
     * @param credentials Mock credentials (ignored in simulation)
     * @return Always successful ConnectionResult
     */
    @Override
    public ConnectionResult connect(PlatformCredentials credentials) {
        logger.info("[INSTAGRAM MOCK] Connecting to Instagram Graph API (simulated)");
        
        // Simulate network latency (20-80ms for OAuth handshake)
        simulateLatency(20, 80);
        
        this.isConnected = true;
        logger.info("[INSTAGRAM MOCK] Connection established successfully");
        
        return ConnectionResult.success("Instagram mock adapter connected (simulation mode)");
    }
    
    /**
     * Simulates sending direct message via Instagram Graph API.
     * 
     * Validation: Checks @username format BEFORE simulating send (per Session 2025-11-24 Q5).
     * Behavior: 90% success rate with random latency 150-400ms (per FR-039).
     * 
     * Educational Note: Real implementation would use Instagram Graph API:
     * POST https://graph.instagram.com/v18.0/me/messages
     * with JSON payload: { "recipient": { "username": "john_doe" }, "message": { "text": "message" } }
     * 
     * Distributed Systems Concept: Instagram has LOWER success rate (90%) vs WhatsApp (95%)
     * and HIGHER latency (150-400ms vs 100-300ms). This simulates real-world scenario where
     * different platforms have different SLAs - system MUST handle partial failures gracefully.
     * 
     * @param externalId Instagram username in @username format (validated)
     * @param messageText Message content (no size limit enforced in mock)
     * @return SendResult with mock platform message ID or error details
     */
    @Override
    public SendResult sendMessage(String externalId, String messageText) {
        logger.info("[INSTAGRAM MOCK] Attempting to send message to externalId={}", externalId);
        
        // Validation: Check @username format (adapter responsibility per Session 2025-11-24 Q5)
        if (!isValidInstagramUsername(externalId)) {
            logger.warn("[INSTAGRAM MOCK] Invalid username format: {}. Expected @username format (e.g., @john_doe)", 
                       externalId);
            return SendResult.failure("invalid_recipient", 
                                     String.format("Username '%s' is not in valid Instagram format. Expected: @username (alphanumeric, underscore, period, 1-30 chars)", externalId));
        }
        
        // Check connection state (fail fast if not connected)
        if (!isConnected) {
            logger.error("[INSTAGRAM MOCK] Cannot send message - adapter not connected");
            return SendResult.failure("not_connected", "Adapter must be connected before sending messages");
        }
        
        // Simulate network latency (150-400ms per FR-039 - higher than WhatsApp)
        int latencyMs = simulateLatency(MIN_LATENCY_MS, MAX_LATENCY_MS);
        logger.debug("[INSTAGRAM MOCK] Simulated API call latency: {}ms", latencyMs);
        
        // Simulate success/failure based on configured rates (per FR-039)
        double randomValue = random.nextDouble();
        
        // 4% connection timeout (higher rate than WhatsApp)
        if (randomValue < CONNECTION_TIMEOUT_RATE) {
            logger.warn("[INSTAGRAM MOCK] Simulated connection_timeout error (4% failure rate)");
            return SendResult.failure("connection_timeout", 
                                     "Instagram Graph API request timed out after 30s");
        }
        
        // 4% rate limit exceeded (higher rate than WhatsApp)
        if (randomValue < CONNECTION_TIMEOUT_RATE + RATE_LIMIT_RATE) {
            logger.warn("[INSTAGRAM MOCK] Simulated rate_limit_exceeded error (4% failure rate)");
            return SendResult.failure("rate_limit_exceeded", 
                                     "Instagram Graph API rate limit: 200 msg/hour exceeded (stricter than WhatsApp)");
        }
        
        // 2% invalid recipient (e.g., username not found or blocked)
        if (randomValue < CONNECTION_TIMEOUT_RATE + RATE_LIMIT_RATE + INVALID_RECIPIENT_RATE) {
            logger.warn("[INSTAGRAM MOCK] Simulated invalid_recipient error (2% failure rate)");
            return SendResult.failure("invalid_recipient", 
                                     String.format("Instagram username %s not found or has blocked messages", externalId));
        }
        
        // 90% success case
        String platformMessageId = generateMockInstagramMessageId();
        logger.info("[INSTAGRAM MOCK] Message sent successfully. platformMessageId={}, to={}, latency={}ms", 
                   platformMessageId, externalId, latencyMs);
        
        return SendResult.success(platformMessageId);
    }
    
    /**
     * Simulates sending file via Instagram Graph API.
     * 
     * Note: P2 feature - not implemented in mock yet. Returns placeholder error.
     * 
     * @param externalId Recipient username
     * @param fileUrl File storage URL
     * @param filename Original filename
     * @return Failure result (not implemented)
     */
    @Override
    public SendResult sendFile(String externalId, String fileUrl, String filename) {
        logger.warn("[INSTAGRAM MOCK] sendFile not implemented - P2 feature deferred");
        return SendResult.failure("not_implemented", "File sending is a P2 feature, deferred from MVP");
    }
    
    @Override
    public Platform getPlatform() {
        return Platform.INSTAGRAM;
    }
    
    /**
     * Validates Instagram username format.
     * 
     * Instagram Username Rules:
     * - Must start with @ symbol
     * - Followed by 1-30 alphanumeric characters, underscores, or periods
     * - No spaces or special characters (except _ and .)
     * - Examples:
     *   - Valid: @john_doe, @jane.smith, @user123, @a
     *   - Invalid: john_doe (missing @), @john doe (space), @user_name! (special char)
     * 
     * Educational Note: Real Instagram API accepts usernames without @ in API payloads,
     * but this mock uses @ prefix for clarity (distinguishes from WhatsApp phone numbers).
     * 
     * @param username Username string to validate
     * @return true if valid Instagram username format, false otherwise
     */
    private boolean isValidInstagramUsername(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username).matches();
    }
    
    /**
     * Generates mock Instagram message ID matching real API format.
     * 
     * Real Instagram message IDs look like: mid.YW1faWQ6MTczMjQ2ODIzMjE3NDoxOjE3MzI0NjgyMzIxNzQ=
     * Format: mid.<base64_encoded_data>
     * 
     * Mock generates: mid.<UUID> (simpler but recognizable as Instagram message ID)
     * 
     * @return Mock platform message ID
     */
    private String generateMockInstagramMessageId() {
        return "mid." + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
    
    /**
     * Simulates network latency by sleeping current thread.
     * 
     * Educational Warning: Thread.sleep() is used ONLY for simulation. In production code,
     * NEVER use Thread.sleep() in request-handling paths - it blocks threads and kills throughput.
     * Use async/reactive patterns (CompletableFuture, Spring WebFlux) instead.
     * 
     * Distributed Systems Concept: Network latency varies between platforms. Instagram Graph API
     * has higher latency (150-400ms) than WhatsApp (100-300ms) due to additional OAuth validation,
     * complex data model, and lower infrastructure investment (non-core Facebook product).
     * 
     * @param minMs Minimum latency in milliseconds
     * @param maxMs Maximum latency in milliseconds
     * @return Actual simulated latency (for logging)
     */
    private int simulateLatency(int minMs, int maxMs) {
        int latencyMs = minMs + random.nextInt(maxMs - minMs + 1);
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("[INSTAGRAM MOCK] Latency simulation interrupted", e);
        }
        return latencyMs;
    }
}
