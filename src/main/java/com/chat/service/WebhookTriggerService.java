package com.chat.service;

import com.chat.dto.WebhookCallbackDto;
import com.chat.model.MessageStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Random;

/**
 * Service for triggering webhook callbacks to simulate platform delivery confirmations.
 * 
 * ARCHITECTURAL NOTE: This service was created to solve a race condition where webhooks
 * were arriving before MongoDB writes completed. By moving webhook triggering FROM
 * Mock Adapters TO Workers (after saveMapping() completes), we guarantee the mapping
 * exists before the webhook callback arrives.
 * 
 * Previous Architecture (BROKEN):
 * 1. MockAdapter.sendMessage() calls triggerDeliveredCallback() @Async
 * 2. @Async starts sleeping (20-30s)
 * 3. sendMessage() returns to Worker
 * 4. Worker calls saveMapping() → MongoDB write starts
 * 5. @Async wakes up and POSTs webhook
 * 6. WebhookController queries MongoDB → MAPPING NOT FOUND (race condition!)
 * 
 * New Architecture (CORRECT):
 * 1. MockAdapter.sendMessage() returns immediately (no webhook trigger)
 * 2. Worker receives SendResult
 * 3. Worker calls saveMapping() → MongoDB write
 * 4. Worker sleeps 2s for MongoDB commit
 * 5. Worker calls ack.acknowledge()
 * 6. Worker calls webhookTriggerService.triggerXxxDelivered() → @Async starts
 * 7. @Async sleeps 1-3s (realistic latency)
 * 8. @Async POSTs webhook
 * 9. WebhookController queries MongoDB → MAPPING FOUND ✓
 * 
 * Benefits:
 * - Eliminates race condition (mapping guaranteed to exist)
 * - Realistic webhook timing (1-3s instead of 60-80s workaround)
 * - Better separation of concerns (Mocks only simulate platform API)
 * - Testable (can mock this service in unit tests)
 */
@Service
@RequiredArgsConstructor
public class WebhookTriggerService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebhookTriggerService.class);
    private final Random random = new Random();
    private final RestTemplate restTemplate;
    
    @Value("${webhook.base-url:http://localhost:8081}")
    private String webhookBaseUrl;
    
    /**
     * Trigger WhatsApp DELIVERED webhook callback asynchronously.
     * 
     * Simulates the delay between message delivery and webhook callback arrival.
     * Real WhatsApp webhooks typically arrive 1-3 seconds after delivery.
     * 
     * @param platformMessageId WhatsApp message ID (wamid.xxx)
     * @param externalId Recipient phone number (E.164 format)
     */
    @Async
    public void triggerWhatsAppDelivered(String platformMessageId, String externalId) {
        try {
            // Simulate realistic webhook latency (1000-3000ms)
            int latencyMs = 1000 + random.nextInt(2000);
            Thread.sleep(latencyMs);
            
            // Build webhook callback payload
            WebhookCallbackDto callback = WebhookCallbackDto.builder()
                    .platformMessageId(platformMessageId)
                    .messageId(platformMessageId) // Will be resolved by WebhookController
                    .status(MessageStatus.DELIVERED)
                    .externalRecipientId(externalId)
                    .timestamp(Instant.now().toString())
                    .build();
            
            // POST to webhook endpoint
            String webhookUrl = webhookBaseUrl + "/api/webhooks/whatsapp";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<WebhookCallbackDto> request = new HttpEntity<>(callback, headers);
            
            restTemplate.postForEntity(webhookUrl, request, String.class);
            
            logger.info("[WEBHOOK TRIGGER] WhatsApp DELIVERED callback sent - platformMessageId: {}, latency: {}ms",
                       platformMessageId, latencyMs);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("[WEBHOOK TRIGGER] WhatsApp callback interrupted - platformMessageId: {}", 
                       platformMessageId, e);
        } catch (Exception e) {
            logger.error("[WEBHOOK TRIGGER] Failed to send WhatsApp callback - platformMessageId: {}, error: {}",
                        platformMessageId, e.getMessage());
            // Don't throw - webhook failures shouldn't break message delivery
        }
    }
    
    /**
     * Trigger Instagram DELIVERED webhook callback asynchronously.
     * 
     * Simulates the delay between message delivery and webhook callback arrival.
     * Real Instagram webhooks typically arrive 1-4 seconds after delivery.
     * 
     * @param platformMessageId Instagram message ID (mid.xxx)
     * @param externalId Recipient username (@xxx)
     */
    @Async
    public void triggerInstagramDelivered(String platformMessageId, String externalId) {
        try {
            // Simulate realistic webhook latency (1000-4000ms, slightly higher than WhatsApp)
            int latencyMs = 1000 + random.nextInt(3000);
            Thread.sleep(latencyMs);
            
            // Build webhook callback payload
            WebhookCallbackDto callback = WebhookCallbackDto.builder()
                    .platformMessageId(platformMessageId)
                    .messageId(platformMessageId) // Will be resolved by WebhookController
                    .status(MessageStatus.DELIVERED)
                    .externalRecipientId(externalId)
                    .timestamp(Instant.now().toString())
                    .build();
            
            // POST to webhook endpoint
            String webhookUrl = webhookBaseUrl + "/api/webhooks/instagram";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<WebhookCallbackDto> request = new HttpEntity<>(callback, headers);
            
            restTemplate.postForEntity(webhookUrl, request, String.class);
            
            logger.info("[WEBHOOK TRIGGER] Instagram DELIVERED callback sent - platformMessageId: {}, latency: {}ms",
                       platformMessageId, latencyMs);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("[WEBHOOK TRIGGER] Instagram callback interrupted - platformMessageId: {}", 
                       platformMessageId, e);
        } catch (Exception e) {
            logger.error("[WEBHOOK TRIGGER] Failed to send Instagram callback - platformMessageId: {}, error: {}",
                        platformMessageId, e.getMessage());
            // Don't throw - webhook failures shouldn't break message delivery
        }
    }
}
