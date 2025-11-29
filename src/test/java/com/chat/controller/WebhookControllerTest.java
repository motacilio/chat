package com.chat.controller;

import com.chat.dto.WebhookCallbackDto;
import com.chat.model.MessageStatus;
import com.chat.service.PlatformMessageMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebhookController.
 * 
 * Tests webhook callback processing from platform mocks (WhatsApp, Instagram).
 */
class WebhookControllerTest {
    
    private WebhookController webhookController;
    private KafkaTemplate<String, com.chat.kafka.v1.StateUpdateEvent> stateKafkaTemplate;
    private PlatformMessageMappingService mappingService;
    
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stateKafkaTemplate = mock(KafkaTemplate.class);
        mappingService = mock(PlatformMessageMappingService.class);
        webhookController = new WebhookController(stateKafkaTemplate, mappingService);
    }
    
    @Test
    @DisplayName("Should process valid WhatsApp DELIVERED callback")
    void testHandleWhatsAppCallback_Delivered() {
        // Arrange
        WebhookCallbackDto callback = WebhookCallbackDto.builder()
                .platformMessageId("wamid.ABC123")
                .messageId("msg-123")
                .status(MessageStatus.DELIVERED)
                .externalRecipientId("+5511987654321")
                .timestamp(Instant.now().toString())
                .build();
        
        // Mock mapping service to return messageId
        when(mappingService.findMessageIdByPlatformMessageId("wamid.ABC123"))
                .thenReturn(Optional.of("msg-123"));
        
        // Act
        var response = webhookController.handleWhatsAppCallback(callback);
        
        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().getSuccess());
        
        // Verify Kafka publish
        ArgumentCaptor<com.chat.kafka.v1.StateUpdateEvent> eventCaptor = ArgumentCaptor.forClass(com.chat.kafka.v1.StateUpdateEvent.class);
        verify(stateKafkaTemplate).send(eq("state-update-events"), eq("msg-123"), eventCaptor.capture());
        
        com.chat.kafka.v1.StateUpdateEvent publishedEvent = eventCaptor.getValue();
        assertEquals("msg-123", publishedEvent.getMessageId());
        assertEquals(com.chat.kafka.v1.StateUpdateEvent.MessageStatus.DELIVERED, publishedEvent.getNewStatus());
        assertEquals("+5511987654321", publishedEvent.getUserId());
    }
    
    @Test
    @DisplayName("Should process valid Instagram READ callback")
    void testHandleInstagramCallback_Read() {
        // Arrange
        WebhookCallbackDto callback = WebhookCallbackDto.builder()
                .platformMessageId("ig_mid.456789")
                .messageId("msg-456")
                .status(MessageStatus.READ)
                .externalRecipientId("@john_doe")
                .timestamp(Instant.now().toString())
                .build();
        
        // Mock mapping service to return messageId
        when(mappingService.findMessageIdByPlatformMessageId("ig_mid.456789"))
                .thenReturn(Optional.of("msg-456"));
        
        // Act
        var response = webhookController.handleInstagramCallback(callback);
        
        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().getSuccess());
        
        // Verify Kafka publish
        ArgumentCaptor<com.chat.kafka.v1.StateUpdateEvent> eventCaptor = ArgumentCaptor.forClass(com.chat.kafka.v1.StateUpdateEvent.class);
        verify(stateKafkaTemplate).send(eq("state-update-events"), eq("msg-456"), eventCaptor.capture());
        
        com.chat.kafka.v1.StateUpdateEvent publishedEvent = eventCaptor.getValue();
        assertEquals("msg-456", publishedEvent.getMessageId());
        assertEquals(com.chat.kafka.v1.StateUpdateEvent.MessageStatus.READ, publishedEvent.getNewStatus());
        assertEquals("@john_doe", publishedEvent.getUserId());
    }
    
    @Test
    @DisplayName("Should reject callback with missing messageId")
    void testHandleWhatsAppCallback_MissingMessageId() {
        // Arrange
        WebhookCallbackDto callback = WebhookCallbackDto.builder()
                .platformMessageId("wamid.ABC123")
                .messageId(null)  // Missing - will fail validation before mapping lookup
                .status(MessageStatus.DELIVERED)
                .externalRecipientId("+5511987654321")
                .build();
        
        // Act
        var response = webhookController.handleWhatsAppCallback(callback);
        
        // Assert - should fail validation before even trying to lookup mapping
        assertEquals(400, response.getStatusCodeValue());
        assertFalse(response.getBody().getSuccess());
        assertNotNull(response.getBody().getMessage());
        assertTrue(response.getBody().getMessage().contains("messageId") || 
                   response.getBody().getMessage().contains("required"),
                   "Expected message about required messageId, got: " + response.getBody().getMessage());
        
        // Verify NO Kafka publish (failed validation)
        verify(stateKafkaTemplate, never()).send(anyString(), anyString(), any());
        
        // Verify mapping service was NOT called (failed early in validation)
        verify(mappingService, never()).findMessageIdByPlatformMessageId(anyString());
    }
    
    @Test
    @DisplayName("Should reject callback with invalid status")
    void testHandleWhatsAppCallback_InvalidStatus() {
        // Arrange
        WebhookCallbackDto callback = WebhookCallbackDto.builder()
                .platformMessageId("wamid.ABC123")
                .messageId("msg-123")
                .status(MessageStatus.SENT)  // Invalid - SENT not expected in webhook
                .externalRecipientId("+5511987654321")
                .build();
        
        // Mock mapping service to return messageId
        when(mappingService.findMessageIdByPlatformMessageId("wamid.ABC123"))
                .thenReturn(Optional.of("msg-123"));
        
        // Act
        var response = webhookController.handleWhatsAppCallback(callback);
        
        // Assert
        assertEquals(400, response.getStatusCodeValue());
        assertFalse(response.getBody().getSuccess());
        assertTrue(response.getBody().getMessage().contains("status"));
    }
    
    @Test
    @DisplayName("Should handle DELIVERED status callback")
    void testHandleWhatsAppCallback_Delivered_Status() {
        // Arrange
        WebhookCallbackDto callback = WebhookCallbackDto.builder()
                .platformMessageId("wamid.ABC123")
                .messageId("msg-789")
                .status(MessageStatus.DELIVERED)
                .externalRecipientId("+5511987654321")
                .errorCode(null)
                .errorMessage(null)
                .timestamp(Instant.now().toString())
                .build();
        
        // Mock mapping service to return messageId
        when(mappingService.findMessageIdByPlatformMessageId("wamid.ABC123"))
                .thenReturn(Optional.of("msg-789"));
        
        // Act
        var response = webhookController.handleWhatsAppCallback(callback);
        
        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().getSuccess());
        
        // Verify Kafka publish with DELIVERED status
        ArgumentCaptor<com.chat.kafka.v1.StateUpdateEvent> eventCaptor = ArgumentCaptor.forClass(com.chat.kafka.v1.StateUpdateEvent.class);
        verify(stateKafkaTemplate).send(eq("state-update-events"), eq("msg-789"), eventCaptor.capture());
        
        com.chat.kafka.v1.StateUpdateEvent publishedEvent = eventCaptor.getValue();
        assertEquals(com.chat.kafka.v1.StateUpdateEvent.MessageStatus.DELIVERED, publishedEvent.getNewStatus());
    }
}
