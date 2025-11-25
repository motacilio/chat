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
 * Responsibility: Mock implementation of WhatsApp Business API adapter for educational/testing purposes.
 * Does NOT: Make real API calls, persist data, handle webhooks (see TelegramBotAdapter for real integration).
 * 
 * Distributed Systems Concept: Simulates realistic production behavior (95% success rate, 100-300ms latency,
 * common error scenarios) to enable testing of retry logic, circuit breakers, and partial failure handling
 * WITHOUT dependency on external WhatsApp API (which requires expensive Business API subscription).
 * 
 * Educational Value: Demonstrates mock implementation patterns - same interface as real adapter but
 * simulated behavior. Teaches students to design for testability and handle unreliable external services.
 * 
 * Mock Behavior (per FR-039 and Session 2025-11-24 Q4):
 * - Success Rate: 95% (5% random failures)
 * - Latency: Random 100-300ms (simulates network + API processing time)
 * - Error Types: connection_timeout (2%), rate_limit_exceeded (2%), invalid_recipient (1%)
 * - External ID Validation: E.164 phone format (+5511987654321)
 * 
 * Pattern: Mock Object - replaces external dependency with controlled simulation.
 */
@Component("whatsappAdapter")
public class WhatsAppMockAdapter implements PlatformAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(WhatsAppMockAdapter.class);
    
    // Mock behavior configuration (per FR-039)
    private static final double SUCCESS_RATE = 0.95; // 95% success
    private static final int MIN_LATENCY_MS = 100;
    private static final int MAX_LATENCY_MS = 300;
    
    // Error distribution (5% total failures)
    private static final double CONNECTION_TIMEOUT_RATE = 0.02; // 2%
    private static final double RATE_LIMIT_RATE = 0.02;         // 2%
    private static final double INVALID_RECIPIENT_RATE = 0.01;  // 1%
    
    // E.164 phone format validation (per Session 2025-11-24 Q5)
    // Pattern: +[country code][area code][number] (e.g., +5511987654321)
    // Minimum 7 digits total (+ followed by 7-15 digits), Maximum 15 digits
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    
    private final Random random = new Random();
    private boolean isConnected = false;
    
    /**
     * Simulates WhatsApp Business API connection establishment.
     * 
     * Educational Note: Real WhatsApp API requires authentication with API key + phone number ID.
     * Mock always succeeds (connection is local state, no network call).
     * 
     * @param credentials Mock credentials (ignored in simulation)
     * @return Always successful ConnectionResult
     */
    @Override
    public ConnectionResult connect(PlatformCredentials credentials) {
        logger.info("[WHATSAPP MOCK] Connecting to WhatsApp Business API (simulated)");
        
        // Simulate network latency (10-50ms for connection handshake)
        simulateLatency(10, 50);
        
        this.isConnected = true;
        logger.info("[WHATSAPP MOCK] Connection established successfully");
        
        return ConnectionResult.success("WhatsApp mock adapter connected (simulation mode)");
    }
    
    /**
     * Simulates sending text message via WhatsApp Business API.
     * 
     * Validation: Checks E.164 phone format BEFORE simulating send (per Session 2025-11-24 Q5).
     * Behavior: 95% success rate with random latency 100-300ms (per FR-039).
     * 
     * Educational Note: Real implementation would use WhatsApp Cloud API:
     * POST https://graph.facebook.com/v18.0/{phone_number_id}/messages
     * with JSON payload: { "to": "+5511987654321", "type": "text", "text": { "body": "message" } }
     * 
     * Distributed Systems Concept: Demonstrates failure modes students MUST handle in production:
     * - Network timeouts (2% rate)
     * - Rate limiting from API provider (2% rate)
     * - Invalid recipient phone numbers (1% rate)
     * 
     * @param externalId WhatsApp phone number in E.164 format (validated)
     * @param messageText Message content (no size limit enforced in mock)
     * @return SendResult with mock platform message ID or error details
     */
    @Override
    public SendResult sendMessage(String externalId, String messageText) {
        logger.info("[WHATSAPP MOCK] Attempting to send message to externalId={}", externalId);
        
        // Validation: Check E.164 format (adapter responsibility per Session 2025-11-24 Q5)
        if (!isValidE164PhoneNumber(externalId)) {
            logger.warn("[WHATSAPP MOCK] Invalid phone number format: {}. Expected E.164 format (e.g., +5511987654321)", 
                       externalId);
            return SendResult.failure("invalid_recipient", 
                                     String.format("Phone number '%s' is not in E.164 format. Expected: +[country][area][number]", externalId));
        }
        
        // Check connection state (fail fast if not connected)
        if (!isConnected) {
            logger.error("[WHATSAPP MOCK] Cannot send message - adapter not connected");
            return SendResult.failure("not_connected", "Adapter must be connected before sending messages");
        }
        
        // Simulate network latency (100-300ms per FR-039)
        int latencyMs = simulateLatency(MIN_LATENCY_MS, MAX_LATENCY_MS);
        logger.debug("[WHATSAPP MOCK] Simulated API call latency: {}ms", latencyMs);
        
        // Simulate success/failure based on configured rates (per FR-039)
        double randomValue = random.nextDouble();
        
        // 2% connection timeout
        if (randomValue < CONNECTION_TIMEOUT_RATE) {
            logger.warn("[WHATSAPP MOCK] Simulated connection_timeout error (2% failure rate)");
            return SendResult.failure("connection_timeout", 
                                     "WhatsApp API request timed out after 30s");
        }
        
        // 2% rate limit exceeded
        if (randomValue < CONNECTION_TIMEOUT_RATE + RATE_LIMIT_RATE) {
            logger.warn("[WHATSAPP MOCK] Simulated rate_limit_exceeded error (2% failure rate)");
            return SendResult.failure("rate_limit_exceeded", 
                                     "WhatsApp Business API rate limit: 1000 msg/sec exceeded");
        }
        
        // 1% invalid recipient (e.g., phone number not registered on WhatsApp)
        if (randomValue < CONNECTION_TIMEOUT_RATE + RATE_LIMIT_RATE + INVALID_RECIPIENT_RATE) {
            logger.warn("[WHATSAPP MOCK] Simulated invalid_recipient error (1% failure rate)");
            return SendResult.failure("invalid_recipient", 
                                     String.format("Phone number %s is not registered on WhatsApp", externalId));
        }
        
        // 95% success case
        String platformMessageId = generateMockWhatsAppMessageId();
        logger.info("[WHATSAPP MOCK] Message sent successfully. platformMessageId={}, to={}, latency={}ms", 
                   platformMessageId, externalId, latencyMs);
        
        return SendResult.success(platformMessageId);
    }
    
    /**
     * Simulates sending file via WhatsApp Business API.
     * 
     * Note: P2 feature - not implemented in mock yet. Returns placeholder error.
     * 
     * @param externalId Recipient phone number
     * @param fileUrl File storage URL
     * @param filename Original filename
     * @return Failure result (not implemented)
     */
    @Override
    public SendResult sendFile(String externalId, String fileUrl, String filename) {
        logger.warn("[WHATSAPP MOCK] sendFile not implemented - P2 feature deferred");
        return SendResult.failure("not_implemented", "File sending is a P2 feature, deferred from MVP");
    }
    
    @Override
    public Platform getPlatform() {
        return Platform.WHATSAPP;
    }
    
    /**
     * Validates phone number format against E.164 international standard.
     * 
     * E.164 Format Rules:
     * - Starts with + (plus sign)
     * - Followed by country code (1-3 digits, cannot start with 0)
     * - Followed by national number (varies by country)
     * - Total length: max 15 digits (excluding +)
     * 
     * Examples:
     * - Valid: +5511987654321 (Brazil mobile)
     * - Valid: +12025551234 (US landline)
     * - Invalid: 5511987654321 (missing +)
     * - Invalid: +0011987654321 (country code cannot start with 0)
     * - Invalid: +55119876543210 (exceeds 15 digits)
     * 
     * Educational Note: Real WhatsApp API performs this validation server-side. Mock
     * validates client-side to catch format errors early (fail fast principle).
     * 
     * @param phoneNumber Phone number string to validate
     * @return true if valid E.164 format, false otherwise
     */
    private boolean isValidE164PhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }
        return E164_PATTERN.matcher(phoneNumber).matches();
    }
    
    /**
     * Generates mock WhatsApp message ID matching real API format.
     * 
     * Real WhatsApp message IDs look like: wamid.HBgLNTUxMTk4NzY1NDMyMRUCABEYEjNCQTdGNjJEODY3QzU3QTJCNQA=
     * Format: wamid.<base64_encoded_data>
     * 
     * Mock generates: wamid.<UUID> (simpler but recognizable as WhatsApp ID)
     * 
     * @return Mock platform message ID
     */
    private String generateMockWhatsAppMessageId() {
        return "wamid." + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
    
    /**
     * Simulates network latency by sleeping current thread.
     * 
     * Educational Warning: Thread.sleep() is used ONLY for simulation. In production code,
     * NEVER use Thread.sleep() in request-handling paths - it blocks threads and kills throughput.
     * Use async/reactive patterns (CompletableFuture, Spring WebFlux) instead.
     * 
     * Distributed Systems Concept: Network latency is a fundamental property of distributed systems.
     * Mock simulates realistic latency distribution (100-300ms for API calls) to test system
     * behavior under real-world conditions (timeout handling, user experience with delays).
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
            logger.warn("[WHATSAPP MOCK] Latency simulation interrupted", e);
        }
        return latencyMs;
    }
}
