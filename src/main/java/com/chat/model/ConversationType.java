package com.chat.model;

/**
 * Conversation type enum (FR-012)
 * 
 * Distinguishes between private 1:1 conversations and group conversations.
 * 
 * Educational Note: Type discrimination enables different business rules:
 * - PRIVATE: Exactly 2 participants, no admin roles needed
 * - GROUP: N participants (max 100 per A-007), requires admin roles for membership management
 */
public enum ConversationType {
    /**
     * PRIVATE: 1:1 conversation between exactly 2 participants.
     * No admin roles - both participants have equal permissions.
     */
    PRIVATE,
    
    /**
     * GROUP: Group conversation with N participants (P3 priority - deferred from MVP).
     * Includes admin roles: creator is initial admin, can add/remove members and promote others.
     */
    GROUP
}
