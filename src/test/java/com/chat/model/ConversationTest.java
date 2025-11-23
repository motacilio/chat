package com.chat.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Conversation entity
 */
class ConversationTest {

    @Test
    void testCreatePrivateConversation() {
        // Given
        String conversationId = "550e8400-e29b-41d4-a716-446655440000";
        String user1 = "user1-id";
        String user2 = "user2-id";

        // When
        Conversation conversation = Conversation.createPrivate(conversationId, user1, user2);

        // Then
        assertNotNull(conversation);
        assertEquals(conversationId, conversation.getConversationId());
        assertEquals(ConversationType.PRIVATE, conversation.getType());
        assertEquals(2, conversation.getParticipants().size());
        assertTrue(conversation.getParticipants().contains(user1));
        assertTrue(conversation.getParticipants().contains(user2));
        assertNotNull(conversation.getCreatedAt());
        assertNotNull(conversation.getLastMessageAt());
    }

    @Test
    void testCreateGroupConversation() {
        // Given
        String conversationId = "550e8400-e29b-41d4-a716-446655440001";
        List<String> participants = List.of("user1", "user2", "user3", "user4");

        // When
        Conversation conversation = Conversation.createGroup(conversationId, participants);

        // Then
        assertNotNull(conversation);
        assertEquals(conversationId, conversation.getConversationId());
        assertEquals(ConversationType.GROUP, conversation.getType());
        assertEquals(4, conversation.getParticipants().size());
        assertNotNull(conversation.getCreatedAt());
        assertNotNull(conversation.getLastMessageAt());
    }

    @Test
    void testIsParticipant() {
        // Given
        Conversation conversation = Conversation.createPrivate("conv-id", "user1", "user2");

        // When & Then
        assertTrue(conversation.isParticipant("user1"));
        assertTrue(conversation.isParticipant("user2"));
        assertFalse(conversation.isParticipant("user3"));
    }

    @Test
    void testBuilderPattern() {
        // Given & When
        Conversation conversation = Conversation.builder()
                .conversationId("test-conv")
                .type(ConversationType.PRIVATE)
                .participants(List.of("u1", "u2"))
                .build();

        // Then
        assertNotNull(conversation);
        assertEquals("test-conv", conversation.getConversationId());
        assertEquals(ConversationType.PRIVATE, conversation.getType());
        assertEquals(2, conversation.getParticipants().size());
    }
}
