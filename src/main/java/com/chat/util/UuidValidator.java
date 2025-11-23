package com.chat.util;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * UUID Validator Utility
 * 
 * Responsibility: Validates UUID format for message_id, conversation_id, user_id.
 * Does NOT: Generate UUIDs (clients generate message_id for idempotency per FR-006).
 * 
 * Educational Note: UUID validation prevents malformed identifiers from propagating through
 * the system, catching client bugs early. UUIDs provide globally unique identifiers without
 * centralized coordination - essential for distributed systems.
 */
public class UuidValidator {
    
    /**
     * UUID regex pattern (RFC 4122 compliant).
     * Format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx where x is hexadecimal digit.
     */
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    
    /**
     * Validate UUID string format.
     * 
     * @param uuid UUID string to validate
     * @return true if valid UUID format
     */
    public static boolean isValid(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }
        return UUID_PATTERN.matcher(uuid).matches();
    }
    
    /**
     * Validate UUID and throw exception if invalid.
     * 
     * @param uuid      UUID string to validate
     * @param fieldName Field name for error message (e.g., "message_id")
     * @throws IllegalArgumentException if UUID is invalid
     */
    public static void validateOrThrow(String uuid, String fieldName) {
        if (!isValid(uuid)) {
            throw new IllegalArgumentException(
                String.format("Invalid UUID format for %s: %s", fieldName, uuid)
            );
        }
    }
    
    /**
     * Generate new random UUID (Version 4).
     * Used for server-generated identifiers (conversation_id, user_id).
     * Clients generate message_id for idempotency (FR-006).
     * 
     * @return UUID string
     */
    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
