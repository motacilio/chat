package com.chat.repository;

import com.chat.model.LinkedAccount;
import com.chat.model.Platform;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * LinkedAccountRepository (data-model.md Entity 7 Repository)
 * 
 * Responsibility: Provides data access methods for LinkedAccount entity.
 * Enables bidirectional lookup between internal users and external platform accounts.
 * 
 * Does NOT:
 * - Handle platform authentication (delegated to PlatformAdapter implementations)
 * - Validate external_id format (validation happens in adapters + API layer)
 * - Manage platform credentials (stored separately, not in LinkedAccount entity)
 * 
 * Indexes (defined in LinkedAccount entity via @CompoundIndexes):
 * 
 * - idx_user_platform (user_id, platform): 
 *   Unique index ensuring one user can only link one account per platform.
 *   Supports O(1) lookup: "What platforms does user X have?"
 *   
 * - idx_platform_external (platform, external_id):
 *   Unique index preventing two users from claiming same external account.
 *   Supports O(1) reverse lookup: "Which user owns Instagram @john_doe?"
 * 
 * Usage Patterns (Multi-Platform Routing):
 * 
 * Pattern 1 - Outbound Message Routing (FR-036):
 * 
 *   When user sends message to recipient, PlatformRoutingService needs to know
 *   which external platforms recipient has linked:
 * 
 *   <pre>
 *   // Get all platforms for recipient
 *   List<LinkedAccount> accounts = repository.findByUserId(recipientId);
 *   
 *   // Result: [LinkedAccount(TELEGRAM, "123456789"), LinkedAccount(WHATSAPP, "+5511...")]
 *   
 *   // Route to each platform
 *   for (LinkedAccount account : accounts) {
 *       PlatformAdapter adapter = adapterRegistry.getAdapter(account.getPlatform());
 *       adapter.sendMessage(account.getExternalId(), messageText);
 *   }
 *   </pre>
 * 
 * Pattern 2 - Inbound Webhook Routing (FR-037):
 * 
 *   When Telegram webhook delivers message from external user, we need to find
 *   which internal user owns that Telegram account:
 * 
 *   <pre>
 *   // Telegram webhook payload: { "from": { "id": "123456789" }, "text": "Hello" }
 *   String telegramUserId = webhookPayload.getFrom().getId();
 *   
 *   // Reverse lookup: Which internal user owns this Telegram account?
 *   Optional<LinkedAccount> account = repository.findByPlatformAndExternalId(
 *       Platform.TELEGRAM, 
 *       telegramUserId
 *   );
 *   
 *   if (account.isEmpty()) {
 *       // Unknown sender - ignore or prompt to link account
 *       return;
 *   }
 *   
 *   // Route message to internal user
 *   String internalUserId = account.get().getUserId();
 *   messageService.deliverInboundMessage(internalUserId, messageText);
 *   </pre>
 * 
 * Pattern 3 - Account Management (Future Link/Unlink API):
 * 
 *   Check if user already linked Instagram before allowing new link:
 * 
 *   <pre>
 *   Optional<LinkedAccount> existing = repository.findByUserIdAndPlatform(
 *       userId, 
 *       Platform.INSTAGRAM
 *   );
 *   
 *   if (existing.isPresent()) {
 *       throw new AlreadyLinkedException("User already linked Instagram account");
 *   }
 *   
 *   // Proceed with link
 *   LinkedAccount newLink = LinkedAccount.create(userId, Platform.INSTAGRAM, "@john_doe");
 *   repository.save(newLink);
 *   </pre>
 * 
 * Educational Note - Why Spring Data MongoDB?
 * 
 * This interface extends MongoRepository, which is part of Spring Data MongoDB.
 * Spring provides automatic implementation at runtime through proxy pattern.
 * 
 * Method Naming Convention (Spring Data Query Derivation):
 * 
 * - findByUserId → Translates to: db.linked_accounts.find({ user_id: <value> })
 * - findByUserIdAndPlatform → { user_id: <userId>, platform: <platform> }
 * - findByPlatformAndExternalId → { platform: <platform>, external_id: <externalId> }
 * 
 * Spring parses method names and generates MongoDB queries automatically.
 * Alternative: Use @Query annotation for complex queries:
 * 
 *   @Query("{ 'user_id': ?0, 'platform': ?1 }")
 *   Optional<LinkedAccount> findByUserIdAndPlatform(String userId, Platform platform);
 * 
 * But for simple queries, method naming is clearer and type-safe.
 * 
 * Performance Considerations:
 * 
 * - All queries use compound indexes → O(1) lookup time
 * - findByUserId returns List (might be 3 items max: WhatsApp, Instagram, Telegram)
 * - Single-item queries use Optional to express "might not exist" semantically
 * - No N+1 query problem: LinkedAccount is standalone entity, no lazy loading
 */
@Repository
public interface LinkedAccountRepository extends MongoRepository<LinkedAccount, String> {
    
    /**
     * Find all linked accounts for a user.
     * Used for outbound message routing: "What platforms does this user have?"
     * 
     * Query: db.linked_accounts.find({ user_id: <userId> })
     * Index: idx_user_platform (partial match on first field)
     * 
     * Example Result:
     * [
     *   LinkedAccount(userId="550e8400...", platform=TELEGRAM, externalId="123456789"),
     *   LinkedAccount(userId="550e8400...", platform=WHATSAPP, externalId="+5511987654321")
     * ]
     * 
     * @param userId Internal user UUID
     * @return List of all linked accounts for this user (0-N platforms)
     */
    List<LinkedAccount> findByUserId(String userId);
    
    /**
     * Find specific platform account for a user.
     * Used for account management: "Does this user have Instagram linked?"
     * 
     * Query: db.linked_accounts.findOne({ user_id: <userId>, platform: <platform> })
     * Index: idx_user_platform (exact match on both fields)
     * 
     * Example Usage:
     * 
     * <pre>
     * // Check if user already linked WhatsApp before allowing link
     * Optional<LinkedAccount> existing = repository.findByUserIdAndPlatform(
     *     userId, 
     *     Platform.WHATSAPP
     * );
     * 
     * if (existing.isPresent()) {
     *     return ResponseEntity.status(409).body("WhatsApp already linked");
     * }
     * </pre>
     * 
     * @param userId   Internal user UUID
     * @param platform External platform (WHATSAPP, INSTAGRAM, TELEGRAM)
     * @return Optional LinkedAccount (empty if user hasn't linked this platform)
     */
    Optional<LinkedAccount> findByUserIdAndPlatform(String userId, Platform platform);
    
    /**
     * Find internal user who owns an external platform account.
     * Used for inbound webhook routing: "Which user owns Telegram account X?"
     * 
     * Query: db.linked_accounts.findOne({ platform: <platform>, external_id: <externalId> })
     * Index: idx_platform_external (exact match on both fields)
     * 
     * Example Usage (Telegram Webhook Handler):
     * 
     * <pre>
     * // Telegram webhook: { "message": { "from": { "id": "123456789" }, "text": "Hi" } }
     * String telegramUserId = webhookPayload.getMessage().getFrom().getId();
     * 
     * // Reverse lookup: Which internal user owns this Telegram account?
     * Optional<LinkedAccount> account = repository.findByPlatformAndExternalId(
     *     Platform.TELEGRAM, 
     *     telegramUserId
     * );
     * 
     * if (account.isEmpty()) {
     *     log.warn("Received message from unknown Telegram user: {}", telegramUserId);
     *     return; // Ignore message or prompt user to link account
     * }
     * 
     * String recipientUserId = account.get().getUserId();
     * messageService.deliverInboundMessage(recipientUserId, messageText);
     * </pre>
     * 
     * Why Optional return type?
     * 
     * - Incoming webhook might be from unlinked account (user hasn't linked Telegram yet)
     * - Spam prevention: Unknown accounts should be ignored gracefully
     * - Optional forces caller to handle "not found" case explicitly (no NullPointerException)
     * 
     * @param platform   External platform (WHATSAPP, INSTAGRAM, TELEGRAM)
     * @param externalId Platform-specific account identifier (phone, username, etc.)
     * @return Optional LinkedAccount (empty if this external account isn't linked to any user)
     */
    Optional<LinkedAccount> findByPlatformAndExternalId(Platform platform, String externalId);
    
    /**
     * Check if user has already linked a specific platform.
     * Used for validation before allowing new link.
     * 
     * Query: db.linked_accounts.count({ user_id: <userId>, platform: <platform> }) > 0
     * Index: idx_user_platform (exact match)
     * 
     * Example Usage:
     * 
     * <pre>
     * if (repository.existsByUserIdAndPlatform(userId, Platform.INSTAGRAM)) {
     *     throw new AlreadyLinkedException("Instagram account already linked");
     * }
     * 
     * // Proceed with linking new account
     * </pre>
     * 
     * Why existsBy instead of findBy?
     * 
     * - More efficient: MongoDB count query instead of fetching entire document
     * - Clearer intent: We only care about existence, not the actual data
     * - Smaller network transfer: Boolean result instead of LinkedAccount object
     * 
     * @param userId   Internal user UUID
     * @param platform External platform to check
     * @return true if user has already linked this platform
     */
    boolean existsByUserIdAndPlatform(String userId, Platform platform);
    
    /**
     * Check if external account is already claimed by any user.
     * Used to prevent duplicate account linking.
     * 
     * Query: db.linked_accounts.count({ platform: <platform>, external_id: <externalId> }) > 0
     * Index: idx_platform_external (exact match)
     * 
     * Example Usage:
     * 
     * <pre>
     * // User tries to link WhatsApp number +5511987654321
     * String phoneNumber = "+5511987654321";
     * 
     * if (repository.existsByPlatformAndExternalId(Platform.WHATSAPP, phoneNumber)) {
     *     throw new AccountClaimedException("This WhatsApp number is already linked to another user");
     * }
     * 
     * // Proceed with linking
     * </pre>
     * 
     * Security Note:
     * 
     * This check is CRITICAL to prevent account hijacking. Without it, malicious user
     * could link another person's WhatsApp number to their internal account.
     * 
     * Defense-in-Depth Strategy:
     * 1. This database-level uniqueness check (idx_platform_external unique index)
     * 2. Platform adapter verification (send OTP code via WhatsApp, user must confirm)
     * 3. Rate limiting on link attempts (prevent brute force)
     * 
     * @param platform   External platform
     * @param externalId Platform-specific account identifier
     * @return true if this external account is already linked to ANY user
     */
    boolean existsByPlatformAndExternalId(Platform platform, String externalId);
    
    /**
     * Delete all linked accounts for a user.
     * Used when user requests account deletion (GDPR compliance, future feature).
     * 
     * Query: db.linked_accounts.deleteMany({ user_id: <userId> })
     * Index: idx_user_platform (partial match on first field)
     * 
     * Example Usage (User Deletion Service):
     * 
     * <pre>
     * public void deleteUserAccount(String userId) {
     *     // 1. Delete all linked platform accounts
     *     linkedAccountRepository.deleteByUserId(userId);
     *     
     *     // 2. Delete user conversations
     *     conversationRepository.deleteByParticipantsContaining(userId);
     *     
     *     // 3. Delete user messages
     *     messageRepository.deleteBySenderId(userId);
     *     
     *     // 4. Delete user entity
     *     userRepository.deleteByUserId(userId);
     * }
     * </pre>
     * 
     * Why void return type instead of List<LinkedAccount>?
     * 
     * - We don't care about deleted documents, only that deletion succeeded
     * - If caller needs to know what was deleted, they should query first
     * - Simpler method signature (no need to handle return value)
     * 
     * Alternative: Return count of deleted documents:
     * 
     *   long deleteByUserId(String userId);
     * 
     * This would return number of deleted LinkedAccount documents (0-3 typically).
     * 
     * @param userId Internal user UUID
     */
    void deleteByUserId(String userId);
}
