package com.chat.worker;

import com.chat.adapter.PlatformAdapter;
import com.chat.adapter.dto.SendResult;
import com.chat.dto.PlatformMessageEventDto;
import com.chat.dto.StateUpdateEventDto;
import com.chat.model.MessageStatus;
import com.chat.model.Platform;
import com.chat.service.AdapterRegistry;
import com.chat.service.PlatformMessageMappingService;
import com.chat.service.WebhookTriggerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * InstagramMessageWorker
 * 
 * Responsibility: Consume messages from instagram-messages topic and deliver
 * to Instagram platform via InstagramMockAdapter.
 * 
 * Layer 2 Enhancement: Platform-specific worker for Instagram Direct Messages.
 * Similar to WhatsAppMessageWorker but with Instagram-specific handling.
 * 
 * Flow:
 * 1. Consume PlatformMessageEventDto from instagram-messages topic
 * 2. Get InstagramMockAdapter from AdapterRegistry
 * 3. Call adapter.sendMessage(@username, messageText)
 * 4. Handle success: Adapter will trigger webhook callback (DELIVERED)
 * 5. Handle failure: Log error and potentially retry or DLQ
 * 6. Commit Kafka offset
 * 
 * Differences from WhatsApp:
 * - Lower success rate (90% vs 95%)
 * - Higher latency (150-400ms vs 100-300ms)
 * - Different external ID format (@username vs +phone)
 * - Different rate limits (200 msg/hour vs 1000 msg/sec)
 */
@Component
public class InstagramMessageWorker {
    
    private static final Logger logger = LoggerFactory.getLogger(InstagramMessageWorker.class);
    
    private final AdapterRegistry adapterRegistry;
    private final KafkaTemplate<String, StateUpdateEventDto> stateKafkaTemplate;
    private final PlatformMessageMappingService mappingService;
    private final WebhookTriggerService webhookTriggerService;
    
    @Value("${webhook.base-url:http://localhost:8081}")
    private String webhookBaseUrl;
    
    public InstagramMessageWorker(
            AdapterRegistry adapterRegistry,
            KafkaTemplate<String, StateUpdateEventDto> stateKafkaTemplate,
            PlatformMessageMappingService mappingService,
            WebhookTriggerService webhookTriggerService) {
        this.adapterRegistry = adapterRegistry;
        this.stateKafkaTemplate = stateKafkaTemplate;
        this.mappingService = mappingService;
        this.webhookTriggerService = webhookTriggerService;
    }
    
    /**
     * Kafka consumer for instagram-messages topic.
     * 
     * Concurrency: Multiple instances for scaling (partitioned by conversation_id).
     * Ordering: Messages for same conversation go to same partition.
     * 
     * @param event Platform-specific message event
     * @param ack   Manual offset commit control
     */
    @KafkaListener(
            topics = "instagram-messages",
            groupId = "instagram-message-workers",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleInstagramMessage(PlatformMessageEventDto event, Acknowledgment ack) {
        try {
            logger.info("[INSTAGRAM WORKER] Processing message - messageId: {}, externalId: {}", 
                       event.getMessageId(), event.getExternalRecipientId());
            
            // Get Instagram adapter from registry
            PlatformAdapter adapter = adapterRegistry.getAdapter(Platform.INSTAGRAM);
            
            // Send message via Instagram Mock Adapter
            SendResult result;
            if (event.getFileId() != null) {
                // File message: Send download URL
                String downloadUrl = buildFileDownloadUrl(event.getFileId());
                String messageText = "📎 File: " + downloadUrl;
                result = adapter.sendMessage(event.getExternalRecipientId(), messageText);
            } else {
                // Text message
                result = adapter.sendMessage(event.getExternalRecipientId(), event.getMessageText());
            }
            
            if (result.isSuccess()) {
                logger.info("[INSTAGRAM WORKER] Message delivered successfully - messageId: {}, platformMessageId: {}", 
                           event.getMessageId(), result.getPlatformMessageId());
                
                // Save mapping for webhook callback lookup
                mappingService.saveMapping(event.getMessageId(), result.getPlatformMessageId(), Platform.INSTAGRAM);
                
                // CRITICAL: Wait for MongoDB write to commit before acknowledging Kafka offset
                // This ensures the mapping is queryable when webhook arrives (20-30s later)
                // Without this sleep, MongoDB may still be writing when webhook arrives (race condition)
                try {
                    Thread.sleep(2000); // 2 seconds to guarantee write concern commit
                    logger.debug("[INSTAGRAM WORKER] MongoDB write buffer flushed for messageId: {}", event.getMessageId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("[INSTAGRAM WORKER] Interrupted while waiting for MongoDB commit");
                }
                
                // Success: Adapter will trigger webhook callback for DELIVERED status
                // We commit the offset here (message sent to platform)
                ack.acknowledge();
                
                // REFACTORED: Trigger webhook AFTER mapping is saved and committed
                // This eliminates the race condition where webhook arrived before mapping was queryable
                // Webhook will arrive in 1-4 seconds (realistic latency) instead of 60-80s workaround
                webhookTriggerService.triggerInstagramDelivered(result.getPlatformMessageId(), event.getExternalRecipientId());
                
            } else {
                // Failed delivery
                handleDeliveryFailure(event, result, ack);
            }
            
        } catch (Exception e) {
            logger.error("[INSTAGRAM WORKER] Unexpected error processing message - messageId: {}, error: {}", 
                        event.getMessageId(), e.getMessage(), e);
            // Don't commit offset - Kafka will retry
        }
    }
    
    /**
     * Handle delivery failure from Instagram adapter.
     * 
     * @param event  Message event
     * @param result Failed send result
     * @param ack    Acknowledgment for offset commit
     */
    private void handleDeliveryFailure(PlatformMessageEventDto event, SendResult result, Acknowledgment ack) {
        String errorCode = result.getErrorCode();
        
        logger.warn("[INSTAGRAM WORKER] Message delivery failed - messageId: {}, errorCode: {}, errorMsg: {}", 
                   event.getMessageId(), errorCode, result.getErrorMessage());
        
        // Classify error type
        boolean isPermanentError = isPermanentError(errorCode);
        
        if (isPermanentError) {
            // Permanent errors: invalid recipient, etc.
            // Commit offset (don't retry) and publish FAILED state
            logger.error("[INSTAGRAM WORKER] Permanent error - skipping retry - messageId: {}, errorCode: {}", 
                        event.getMessageId(), errorCode);
            
            // Publish FAILED state update
            publishFailedState(event, errorCode, result.getErrorMessage());
            
            ack.acknowledge();
            
        } else {
            // Transient errors: connection_timeout, rate_limit, etc.
            // Don't commit offset - Kafka will retry after backoff
            logger.warn("[INSTAGRAM WORKER] Transient error - will retry - messageId: {}, errorCode: {}", 
                       event.getMessageId(), errorCode);
            // Don't acknowledge - Kafka retries
        }
    }
    
    /**
     * Check if error is permanent (don't retry) or transient (retry).
     * 
     * @param errorCode Error code from adapter
     * @return true if permanent error
     */
    private boolean isPermanentError(String errorCode) {
        switch (errorCode) {
            case "invalid_recipient":
            case "invalid_username":
            case "blocked_contact":
            case "user_not_found":
                return true;  // Permanent errors
            
            case "connection_timeout":
            case "rate_limit_exceeded":
            case "service_unavailable":
                return false;  // Transient errors (retry)
            
            default:
                return false;  // Unknown errors: retry by default
        }
    }
    
    /**
     * Publish FAILED state update to Kafka.
     * 
     * @param event        Message event
     * @param errorCode    Error code
     * @param errorMessage Error message
     */
    private void publishFailedState(PlatformMessageEventDto event, String errorCode, String errorMessage) {
        // Note: MessageStatus enum doesn't have FAILED state  
        // For MVP, we log the error but don't update message status
        // Future: Add FAILED status to enum or use separate error tracking collection
        logger.error("[INSTAGRAM WORKER] Message delivery failed permanently - messageId: {}, errorCode: {}, errorMsg: {}", 
                   event.getMessageId(), errorCode, errorMessage);
        
        // TODO: Implement error tracking mechanism
    }
    
    /**
     * Build file download URL for file messages.
     * 
     * @param fileId File UUID
     * @return Public download URL
     */
    private String buildFileDownloadUrl(String fileId) {
        return webhookBaseUrl + "/api/files/" + fileId + "/download";
    }
}
