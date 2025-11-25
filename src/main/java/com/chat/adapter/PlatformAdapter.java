package com.chat.adapter;

import com.chat.adapter.dto.ConnectionResult;
import com.chat.adapter.dto.PlatformCredentials;
import com.chat.adapter.dto.SendResult;

/**
 * Responsibility: Defines contract for external platform integrations (WhatsApp, Instagram, Telegram).
 * Does NOT: Handle message routing logic (see PlatformRoutingService), validate business rules.
 * 
 * Distributed Systems Concept: Adapter pattern for multi-platform integration. Provides abstraction
 * over external APIs, enabling transparent replacement of implementations (real vs mock adapters).
 * 
 * Educational Value: Demonstrates hexagonal architecture - domain core remains independent of
 * external platform details. Each adapter encapsulates platform-specific concerns (authentication,
 * message format, error handling).
 * 
 * Pattern: Strategy Pattern - each platform is a different strategy for message delivery.
 * 
 * Usage Example:
 * <pre>
 * PlatformAdapter whatsapp = adapterRegistry.getAdapter(Platform.WHATSAPP);
 * ConnectionResult connection = whatsapp.connect(credentials);
 * if (connection.isSuccess()) {
 *     SendResult result = whatsapp.sendMessage("+5511987654321", "Hello!");
 *     if (result.isSuccess()) {
 *         String platformMessageId = result.getPlatformMessageId();
 *     }
 * }
 * </pre>
 */
public interface PlatformAdapter {
    
    /**
     * Establishes connection to the external platform using provided credentials.
     * 
     * Side Effects: Network I/O to platform API, may create session state.
     * 
     * @param credentials Platform-specific authentication credentials (API keys, tokens, etc.)
     * @return ConnectionResult with success status and error details if failed
     * 
     * Distributed Systems Concept: Connection establishment as explicit operation enables
     * circuit breaker pattern (fail fast if platform unavailable), retry logic, and
     * connection pooling for performance.
     */
    ConnectionResult connect(PlatformCredentials credentials);
    
    /**
     * Sends text message to external platform recipient.
     * 
     * Side Effects: Network I/O to platform API, may throw unchecked exceptions on failure.
     * 
     * @param externalId Platform-specific recipient identifier (phone number for WhatsApp,
     *                   username for Instagram, user_id for Telegram). Must be validated
     *                   by adapter implementation according to platform format rules.
     * @param messageText Message content (plain text). Platform may have size limits.
     * @return SendResult with platform-assigned message ID if successful, error details if failed
     * 
     * Educational Note: Each adapter is responsible for validating externalId format per
     * Session 2025-11-24 Q5 clarification:
     * - WhatsApp: E.164 phone format (e.g., "+5511987654321")
     * - Instagram: @username pattern (e.g., "@john_doe")
     * - Telegram: numeric user_id (e.g., "123456789")
     * 
     * Distributed Systems Concept: Returns platform message ID for correlation - enables
     * tracking message delivery across system boundaries (internal message_id maps to
     * external platformMessageId).
     */
    SendResult sendMessage(String externalId, String messageText);
    
    /**
     * Sends file to external platform recipient.
     * 
     * Side Effects: Network I/O to platform API, may upload file bytes, throws on failure.
     * 
     * @param externalId Platform-specific recipient identifier (validated by adapter)
     * @param fileUrl URL to file storage (MinIO pre-signed URL or local path)
     * @param filename Original filename for recipient display
     * @return SendResult with platform message ID if successful, error details if failed
     * 
     * Note: P2 feature - deferred from MVP. Placeholder for future implementation.
     */
    SendResult sendFile(String externalId, String fileUrl, String filename);
    
    /**
     * Returns platform identifier for this adapter instance.
     * 
     * @return Platform enum value (WHATSAPP, INSTAGRAM, TELEGRAM)
     * 
     * Used by AdapterRegistry for routing: registry.getAdapter(Platform.WHATSAPP).
     */
    com.chat.model.Platform getPlatform();
}
