package com.chat.controller;

import com.chat.dto.WebhookCallbackDto;
import com.chat.kafka.v1.StateUpdateEvent;
import com.chat.model.MessageStatus;
import com.chat.service.PlatformMessageMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Optional;

/**
 * WebhookController
 * 
 * Responsibility: Receive webhook callbacks from external platforms
 * (WhatsApp, Instagram mocks) reporting message delivery and read status.
 * 
 * Layer 2 Enhancement: Enables bidirectional communication with platform mocks.
 * Simulates real webhook callbacks from WhatsApp Business API, Instagram Graph API.
 * 
 * Flow:
 * 1. Platform mock sends HTTP POST with WebhookCallbackDto
 * 2. Controller validates callback payload
 * 3. Publishes StateUpdateEventDto to state-update-events topic
 * 4. MessageStateUpdateWorker consumes and updates MongoDB
 * 5. StreamingService notifies sender via gRPC stream
 * 
 * Security (Future):
 * - Webhook signature validation (HMAC-SHA256)
 * - IP whitelist for platform webhook sources
 * - Rate limiting to prevent abuse
 * 
 * Endpoints:
 * - POST /api/webhooks/whatsapp - WhatsApp delivery/read callbacks
 * - POST /api/webhooks/instagram - Instagram delivery/read callbacks
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {
    
    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);
    
    private final KafkaTemplate<String, com.chat.kafka.v1.StateUpdateEvent> stateKafkaTemplate;
    private final PlatformMessageMappingService mappingService;
    
    public WebhookController(
            KafkaTemplate<String, com.chat.kafka.v1.StateUpdateEvent> stateKafkaTemplate,
            PlatformMessageMappingService mappingService) {
        this.stateKafkaTemplate = stateKafkaTemplate;
        this.mappingService = mappingService;
    }
    
    /**
     * Webhook endpoint for WhatsApp delivery/read callbacks.
     * 
     * Called by WhatsAppMockAdapter after simulated message delivery.
     * 
     * Example Payload:
     * {
     *   "platformMessageId": "wamid.ABC123",
     *   "messageId": "660e8400-e29b-41d4-a716-446655440000",
     *   "status": "DELIVERED",
     *   "externalRecipientId": "+5511987654321",
     *   "timestamp": "2025-11-24T10:30:00Z"
     * }
     * 
     * @param callback Webhook callback data
     * @return 200 OK if processed successfully
     */
    @PostMapping("/whatsapp")
    public ResponseEntity<WebhookResponse> handleWhatsAppCallback(@RequestBody WebhookCallbackDto callback) {
        logger.info("[WEBHOOK] WhatsApp callback received - platformMessageId: {}, status: {}", 
                   callback.getPlatformMessageId(), callback.getStatus());
        
        try {
            // Validate callback data
            validateCallback(callback);
            
            // Lookup internal messageId from platformMessageId (with retry for race condition)
            Optional<String> messageIdOpt = findMessageIdWithRetry(callback.getPlatformMessageId(), 5, 300);
            if (messageIdOpt.isEmpty()) {
                logger.error("[WEBHOOK] No messageId mapping found for platformMessageId: {} after retries", 
                           callback.getPlatformMessageId());
                return ResponseEntity.badRequest().body(WebhookResponse.builder()
                        .success(false)
                        .message("Unknown platformMessageId - no mapping found")
                        .build());
            }
            
            String messageId = messageIdOpt.get();
            logger.info("[WEBHOOK] Resolved messageId: {} from platformMessageId: {}", 
                       messageId, callback.getPlatformMessageId());
            
            // Publish state update event to Kafka
            com.google.protobuf.Timestamp timestamp = com.google.protobuf.util.Timestamps.fromMillis(
                callback.getTimestamp() != null ? 
                    Instant.parse(callback.getTimestamp()).toEpochMilli() : 
                    Instant.now().toEpochMilli()
            );
            
            com.chat.kafka.v1.StateUpdateEvent stateEvent = com.chat.kafka.v1.StateUpdateEvent.newBuilder()
                    .setMessageId(messageId)
                    .setNewStatus(mapToProtobufStatus(callback.getStatus()))
                    .setUserId(callback.getExternalRecipientId())
                    .setTimestamp(timestamp)
                    .build();
            
            stateKafkaTemplate.send("state-update-events", messageId, stateEvent);
            
            logger.info("[WEBHOOK] WhatsApp callback processed - messageId: {}, status: {}", 
                       messageId, callback.getStatus());
            
            return ResponseEntity.ok(WebhookResponse.builder()
                    .success(true)
                    .message("Callback processed successfully")
                    .build());
            
        } catch (IllegalArgumentException e) {
            logger.error("[WEBHOOK] Invalid WhatsApp callback - platformMessageId: {}, error: {}", 
                        callback.getPlatformMessageId(), e.getMessage());
            return ResponseEntity.badRequest().body(WebhookResponse.builder()
                    .success(false)
                    .message("Invalid callback: " + e.getMessage())
                    .build());
            
        } catch (Exception e) {
            logger.error("[WEBHOOK] Error processing WhatsApp callback - messageId: {}, error: {}", 
                        callback.getMessageId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(WebhookResponse.builder()
                    .success(false)
                    .message("Internal error processing callback")
                    .build());
        }
    }
    
    /**
     * Webhook endpoint for Instagram delivery/read callbacks.
     * 
     * Called by InstagramMockAdapter after simulated message delivery.
     * 
     * Example Payload:
     * {
     *   "platformMessageId": "ig_mid.456789",
     *   "messageId": "660e8400-e29b-41d4-a716-446655440000",
     *   "status": "DELIVERED",
     *   "externalRecipientId": "@john_doe",
     *   "timestamp": "2025-11-24T10:30:00Z"
     * }
     * 
     * @param callback Webhook callback data
     * @return 200 OK if processed successfully
     */
    @PostMapping("/instagram")
    public ResponseEntity<WebhookResponse> handleInstagramCallback(@RequestBody WebhookCallbackDto callback) {
        logger.info("[WEBHOOK] Instagram callback received - platformMessageId: {}, status: {}", 
                   callback.getPlatformMessageId(), callback.getStatus());
        
        try {
            // Validate callback data
            validateCallback(callback);
            
            // Lookup internal messageId from platformMessageId (with retry for race condition)
            Optional<String> messageIdOpt = findMessageIdWithRetry(callback.getPlatformMessageId(), 5, 300);
            if (messageIdOpt.isEmpty()) {
                logger.error("[WEBHOOK] No messageId mapping found for platformMessageId: {} after retries", 
                           callback.getPlatformMessageId());
                return ResponseEntity.badRequest().body(WebhookResponse.builder()
                        .success(false)
                        .message("Unknown platformMessageId - no mapping found")
                        .build());
            }
            
            String messageId = messageIdOpt.get();
            logger.info("[WEBHOOK] Resolved messageId: {} from platformMessageId: {}", 
                       messageId, callback.getPlatformMessageId());
            
            // Publish state update event to Kafka
            com.google.protobuf.Timestamp timestamp = com.google.protobuf.util.Timestamps.fromMillis(
                callback.getTimestamp() != null ? 
                    Instant.parse(callback.getTimestamp()).toEpochMilli() : 
                    Instant.now().toEpochMilli()
            );
            
            com.chat.kafka.v1.StateUpdateEvent stateEvent = com.chat.kafka.v1.StateUpdateEvent.newBuilder()
                    .setMessageId(messageId)
                    .setNewStatus(mapToProtobufStatus(callback.getStatus()))
                    .setUserId(callback.getExternalRecipientId())
                    .setTimestamp(timestamp)
                    .build();
            
            stateKafkaTemplate.send("state-update-events", messageId, stateEvent);
            
            logger.info("[WEBHOOK] Instagram callback processed - messageId: {}, status: {}", 
                       messageId, callback.getStatus());
            
            return ResponseEntity.ok(WebhookResponse.builder()
                    .success(true)
                    .message("Callback processed successfully")
                    .build());
            
        } catch (IllegalArgumentException e) {
            logger.error("[WEBHOOK] Invalid Instagram callback - platformMessageId: {}, error: {}", 
                        callback.getPlatformMessageId(), e.getMessage());
            return ResponseEntity.badRequest().body(WebhookResponse.builder()
                    .success(false)
                    .message("Invalid callback: " + e.getMessage())
                    .build());
            
        } catch (Exception e) {
            logger.error("[WEBHOOK] Error processing Instagram callback - platformMessageId: {}, error: {}", 
                        callback.getPlatformMessageId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(WebhookResponse.builder()
                    .success(false)
                    .message("Internal error processing callback")
                    .build());
        }
    }
    
    /**
     * Find messageId by platformMessageId with retry logic.
     * 
     * Handles race condition where webhook callback arrives before worker
     * finishes saving the mapping to MongoDB.
     * 
     * @param platformMessageId Platform message ID
     * @param maxRetries Maximum number of retry attempts
     * @param delayMs Delay between retries in milliseconds
     * @return Message ID if found
     */
    private Optional<String> findMessageIdWithRetry(String platformMessageId, int maxRetries, long delayMs) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Optional<String> result = mappingService.findMessageIdByPlatformMessageId(platformMessageId);
            if (result.isPresent()) {
                if (attempt > 1) {
                    logger.debug("[WEBHOOK] Found mapping on attempt {} for platformMessageId: {}", 
                               attempt, platformMessageId);
                }
                return result;
            }
            
            if (attempt < maxRetries) {
                try {
                    logger.debug("[WEBHOOK] Mapping not found, retrying in {}ms (attempt {}/{})", 
                               delayMs, attempt, maxRetries);
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("[WEBHOOK] Retry interrupted for platformMessageId: {}", platformMessageId);
                    return Optional.empty();
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Validate webhook callback data.
     * 
     * @param callback Webhook callback
     * @throws IllegalArgumentException if validation fails
     */
    private void validateCallback(WebhookCallbackDto callback) {
        if (callback.getMessageId() == null || callback.getMessageId().trim().isEmpty()) {
            throw new IllegalArgumentException("messageId is required");
        }
        
        if (callback.getStatus() == null) {
            throw new IllegalArgumentException("status is required");
        }
        
        // Validate status is valid transition (DELIVERED or READ typically)
        if (callback.getStatus() != MessageStatus.DELIVERED && 
            callback.getStatus() != MessageStatus.READ) {
            throw new IllegalArgumentException("Invalid status: " + callback.getStatus());
        }
        
        if (callback.getExternalRecipientId() == null || callback.getExternalRecipientId().trim().isEmpty()) {
            throw new IllegalArgumentException("externalRecipientId is required");
        }
    }
    
    /**
     * Map domain MessageStatus to Protobuf StateUpdateEvent.MessageStatus.
     */
    private StateUpdateEvent.MessageStatus mapToProtobufStatus(MessageStatus status) {
        switch (status) {
            case SENT:
                return StateUpdateEvent.MessageStatus.SENT;
            case DELIVERED:
                return StateUpdateEvent.MessageStatus.DELIVERED;
            case READ:
                return StateUpdateEvent.MessageStatus.READ;
            default:
                return StateUpdateEvent.MessageStatus.MESSAGE_STATUS_UNSPECIFIED;
        }
    }
    
    /**
     * Response DTO for webhook endpoints.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WebhookResponse {
        private Boolean success;
        private String message;
    }
}
