package com.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * LinkedAccount Entity (data-model.md Entity 7)
 * 
 * Responsibility: Maps internal user_id to external platform account identifiers.
 * Enables multi-platform message routing via adapter pattern (FR-036).
 * 
 * Does NOT: 
 * - Handle platform authentication (delegated to PlatformAdapter implementations)
 * - Sync account status (polling/webhook feature deferred)
 * - Store platform-specific metadata (credentials, tokens - handled separately)
 * 
 * Distributed Systems Concept: Integration Pattern
 * 
 * This entity is the bridge between our internal user model and external messaging platforms.
 * It follows the "Adapter Pattern" where each platform (WhatsApp, Instagram, Telegram) has
 * a unique identifier format:
 * 
 * - WhatsApp: E.164 phone number (e.g., "+5511987654321")
 * - Instagram: Username with @ prefix (e.g., "@john_doe")
 * - Telegram: Numeric user_id from Bot API (e.g., "123456789")
 * 
 * This separation allows:
 * 1. One internal user to have multiple platform accounts
 * 2. Platform-specific validation rules to live in adapters (not entity)
 * 3. Routing service to lookup accounts without coupling to platform details
 * 
 * Example Scenario (FR-036 Multi-Platform Routing):
 * 
 *   User sends message to recipient who has linked Telegram and WhatsApp:
 *   1. MessageService receives message with recipient_id
 *   2. PlatformRoutingService queries LinkedAccountRepository.findByUserId(recipient_id)
 *   3. Returns [LinkedAccount(TELEGRAM, "123456789"), LinkedAccount(WHATSAPP, "+5511...")]
 *   4. Routing service calls appropriate adapters for each platform
 *   5. Message delivered via Telegram Bot API AND WhatsApp Business API simultaneously
 * 
 * Indexes Rationale (data-model.md Entity 7):
 * 
 * - idx_user_platform (user_id, platform): Unique constraint ensures one user can only link
 *   one Instagram account, one WhatsApp number, etc. Supports O(1) lookup when routing
 *   outbound messages: "What platforms does this recipient have?"
 * 
 * - idx_platform_external (platform, external_id): Unique constraint prevents two internal
 *   users from claiming the same external account. Supports O(1) reverse lookup for inbound
 *   webhooks: "Which internal user owns Telegram account 123456789?"
 * 
 * Why MongoDB for This Entity?
 * 
 * - Simple key-value mapping with no complex relationships
 * - Flexible schema if we need to add platform-specific metadata later (e.g., display_name)
 * - Same database as User entity simplifies transaction logic (future feature)
 * - Unique compound indexes enforce business rules at database level
 * 
 * Educational Note - External ID Validation:
 * 
 * This entity does NOT validate external_id format (e.g., phone number regex, username pattern).
 * Validation happens in two layers:
 * 
 * 1. Link Account API Endpoint (future): Validates format before persisting LinkedAccount
 * 2. PlatformAdapter.connect(): Real adapter validates credentials when establishing connection
 * 
 * This follows "Defense in Depth" - multiple validation layers, but entity remains agnostic
 * to platform-specific rules. Entity only enforces structural constraints (uniqueness).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "linked_accounts")
@CompoundIndexes({
    @CompoundIndex(
        name = "idx_user_platform",
        def = "{'user_id': 1, 'platform': 1}",
        unique = true
    ),
    @CompoundIndex(
        name = "idx_platform_external",
        def = "{'platform': 1, 'external_id': 1}",
        unique = true
    )
})
public class LinkedAccount {
    
    /**
     * MongoDB internal ID (not exposed to clients)
     */
    @Id
    private String id;
    
    /**
     * Internal user_id (UUID - references User.userId)
     * 
     * This is the canonical user identifier in our system. Multiple LinkedAccount
     * documents can have the same user_id (one per platform), but the compound
     * index (user_id, platform) ensures uniqueness per platform.
     */
    private String userId;
    
    /**
     * External platform identifier
     * 
     * Platform enum from com.chat.model.Platform:
     * - WHATSAPP: WhatsApp Business API integration (FR-036)
     * - INSTAGRAM: Instagram Graph API integration (FR-036)
     * - TELEGRAM: Telegram Bot API integration (FR-037)
     * 
     * This field is part of both compound indexes to enable bidirectional lookup:
     * - "What platforms does user X have?" → query by (user_id)
     * - "Which user owns Instagram @john_doe?" → query by (platform, external_id)
     */
    private Platform platform;
    
    /**
     * Platform-specific account identifier
     * 
     * Format varies by platform (validation delegated to adapters):
     * 
     * - WHATSAPP: E.164 phone number (e.g., "+5511987654321")
     *   Validated by WhatsAppAdapter using regex: ^\+[1-9]\d{1,14}$
     * 
     * - INSTAGRAM: Username with @ prefix (e.g., "@john_doe")
     *   Validated by InstagramAdapter using regex: ^@[a-zA-Z0-9._]{1,30}$
     * 
     * - TELEGRAM: Numeric user_id from Bot API (e.g., "123456789")
     *   Validated by TelegramAdapter, obtained via /getMe endpoint or webhook
     * 
     * Why String instead of dedicated types?
     * 
     * - Flexibility: Future platforms may have different identifier formats
     * - Simplicity: No need for polymorphic entity hierarchy (complexity overkill)
     * - Validation: Platform-specific rules belong in adapters, not domain model
     * 
     * This is a trade-off: We sacrifice compile-time type safety for runtime flexibility.
     * Alternative design would be:
     * 
     *   class WhatsAppLinkedAccount { String phoneNumber; }
     *   class InstagramLinkedAccount { String username; }
     *   class TelegramLinkedAccount { String userId; }
     * 
     * But this creates 3x entities, 3x repositories, more complex query logic.
     * For a POC with 3 platforms, String + adapter validation is sufficient.
     */
    private String externalId;
    
    /**
     * Timestamp when account was linked
     * 
     * Use cases:
     * - Audit trail: When did user link this platform?
     * - Future feature: "Unlink accounts older than 90 days" for security
     * - Analytics: Track platform adoption over time
     */
    private Instant linkedAt;
    
    /**
     * Factory method to create new linked account with auto-generated timestamp.
     * 
     * Example Usage (in hypothetical LinkAccountService):
     * 
     * <pre>
     * public void linkWhatsApp(String userId, String phoneNumber) {
     *     // 1. Validate phone format (E.164)
     *     if (!phoneNumber.matches("^\\+[1-9]\\d{1,14}$")) {
     *         throw new IllegalArgumentException("Invalid E.164 phone: " + phoneNumber);
     *     }
     *     
     *     // 2. Test connection via adapter
     *     PlatformAdapter adapter = adapterRegistry.getAdapter(Platform.WHATSAPP);
     *     ConnectionResult result = adapter.connect(new WhatsAppCredentials(phoneNumber, apiKey));
     *     if (!result.isSuccess()) {
     *         throw new PlatformConnectionException("WhatsApp connection failed");
     *     }
     *     
     *     // 3. Persist link
     *     LinkedAccount account = LinkedAccount.create(userId, Platform.WHATSAPP, phoneNumber);
     *     linkedAccountRepository.save(account);
     * }
     * </pre>
     * 
     * @param userId     Internal user UUID
     * @param platform   External platform (WHATSAPP, INSTAGRAM, TELEGRAM)
     * @param externalId Platform-specific account identifier
     * @return LinkedAccount instance with linkedAt set to current time
     */
    public static LinkedAccount create(String userId, Platform platform, String externalId) {
        return LinkedAccount.builder()
                .userId(userId)
                .platform(platform)
                .externalId(externalId)
                .linkedAt(Instant.now())
                .build();
    }
    
    /**
     * Human-readable representation for logging and debugging.
     * 
     * Does NOT include sensitive data (external_id might be PII).
     * For production logging, use structured logging with field-level redaction.
     * 
     * @return String representation like "LinkedAccount(userId=550e8400..., platform=WHATSAPP)"
     */
    @Override
    public String toString() {
        return String.format(
            "LinkedAccount(userId=%s, platform=%s, linkedAt=%s)",
            userId != null ? userId.substring(0, 8) + "..." : "null",
            platform,
            linkedAt
        );
    }
}
