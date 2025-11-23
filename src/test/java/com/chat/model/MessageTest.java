package com.chat.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Message entity
 */
class MessageTest {

    @Test
    void testCreateTextMessage() {
        // Given
        String messageId = "550e8400-e29b-41d4-a716-446655440000";
        String conversationId = "550e8400-e29b-41d4-a716-446655440001";
        String senderId = "550e8400-e29b-41d4-a716-446655440002";
        String messageText = "Hello, World!";
        Long sequenceNumber = 1L;

        // When
        Message message = Message.createTextMessage(messageId, conversationId, senderId, messageText, sequenceNumber);

        // Then
        assertNotNull(message);
        assertEquals(messageId, message.getMessageId());
        assertEquals(conversationId, message.getConversationId());
        assertEquals(senderId, message.getSenderId());
        assertEquals(messageText, message.getMessageText());
        assertEquals(sequenceNumber, message.getSequenceNumber());
        assertNotNull(message.getTimestamp());
        assertEquals(MessageStatus.SENT, message.getCurrentStatus());
    }

    @Test
    void testBuilderPattern() {
        // Given
        Instant now = Instant.now();
        
        // When
        Message message = Message.builder()
                .messageId("test-id")
                .conversationId("conv-id")
                .senderId("user-id")
                .messageText("Test message")
                .timestamp(now)
                .sequenceNumber(5L)
                .build();

        // Then
        assertNotNull(message);
        assertEquals("test-id", message.getMessageId());
        assertEquals("conv-id", message.getConversationId());
        assertEquals("user-id", message.getSenderId());
        assertEquals("Test message", message.getMessageText());
        assertEquals(now, message.getTimestamp());
        assertEquals(5L, message.getSequenceNumber());
    }

    @Test
    void testAddStateTransition() {
        // Given
        Message message = Message.createTextMessage("id1", "conv1", "user1", "text", 1L);
        
        // When
        message.addStateTransition(MessageStatus.DELIVERED, "recipient1");
        message.addStateTransition(MessageStatus.READ, "recipient1");

        // Then
        assertEquals(MessageStatus.READ, message.getCurrentStatus());
        assertEquals(3, message.getStateHistory().size()); // SENT + DELIVERED + READ
    }

    @Test
    void testGetCurrentStatusWhenNoHistory() {
        // Given
        Message message = Message.builder()
                .messageId("id")
                .conversationId("conv")
                .senderId("sender")
                .messageText("text")
                .sequenceNumber(1L)
                .build();

        // When
        MessageStatus status = message.getCurrentStatus();

        // Then
        assertEquals(MessageStatus.SENT, status);
    }
}
