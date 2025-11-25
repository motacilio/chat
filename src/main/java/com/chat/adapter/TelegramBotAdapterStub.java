package com.chat.adapter;

import com.chat.adapter.dto.ConnectionResult;
import com.chat.adapter.dto.PlatformCredentials;
import com.chat.adapter.dto.SendResult;
import com.chat.model.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Responsibility: Stub for future Telegram Bot API integration (T073 - deferred).
 * Does NOT: Implement real Telegram integration (placeholder for future development).
 * 
 * Educational Value: Demonstrates stub/placeholder pattern - provides minimal implementation
 * to satisfy Spring dependency injection while real integration is pending.
 * 
 * Pattern: Stub Object - temporary implementation that returns "not implemented" errors.
 * 
 * Future Implementation Plan (T073):
 * - Use telegrambots library (https://github.com/rubenlagus/TelegramBots)
 * - Implement real Telegram Bot API authentication with bot token
 * - Handle incoming messages via webhook (TelegramWebhookController - T079)
 * - Send messages via sendMessage API endpoint
 * - Validate numeric Telegram user_id format (e.g., "123456789")
 * 
 * Why Stub Instead of Leaving Unimplemented?
 * - Spring autowiring: AdapterRegistry constructor needs all 3 adapters injected
 * - Without stub: Spring fails to start with "Could not autowire @Qualifier('telegramAdapter')"
 * - With stub: System starts, other adapters (WhatsApp, Instagram mocks) work independently
 * - Incremental development: Can implement Telegram later without breaking existing code
 * 
 * @see TelegramBotAdapter Real implementation in future PR
 */
@Component("telegramAdapter")
public class TelegramBotAdapterStub implements PlatformAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotAdapterStub.class);
    
    /**
     * Stub implementation - always returns "not implemented" error.
     * 
     * Educational Note: Real implementation would authenticate with Telegram Bot API
     * using bot token obtained from @BotFather.
     */
    @Override
    public ConnectionResult connect(PlatformCredentials credentials) {
        logger.warn("[TELEGRAM STUB] connect() called - not implemented (T073 deferred)");
        return ConnectionResult.failure("Telegram integration is T073 task, deferred to post-MVP");
    }
    
    /**
     * Stub implementation - always returns "not implemented" error.
     * 
     * Educational Note: Real implementation would use Telegram Bot API sendMessage endpoint:
     * https://api.telegram.org/bot{token}/sendMessage
     * with JSON payload: { "chat_id": 123456789, "text": "message" }
     * 
     * @param externalId Telegram user_id (numeric, e.g., "123456789")
     * @param messageText Message content
     */
    @Override
    public SendResult sendMessage(String externalId, String messageText) {
        logger.warn("[TELEGRAM STUB] sendMessage() called for externalId={} - not implemented (T073 deferred)", 
                   externalId);
        return SendResult.failure("not_implemented", 
                                 "Telegram message sending is T073 task, deferred to post-MVP");
    }
    
    /**
     * Stub implementation - always returns "not implemented" error.
     * 
     * Educational Note: Real implementation would use Telegram Bot API sendDocument endpoint.
     */
    @Override
    public SendResult sendFile(String externalId, String fileUrl, String filename) {
        logger.warn("[TELEGRAM STUB] sendFile() called - not implemented (T073 deferred)");
        return SendResult.failure("not_implemented", 
                                 "Telegram file sending is T073 task, deferred to post-MVP");
    }
    
    @Override
    public Platform getPlatform() {
        return Platform.TELEGRAM;
    }
}
