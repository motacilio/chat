package com.chat.controller;

import com.chat.dto.webhook.TelegramWebhookDto;
import com.chat.service.TelegramWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * Controller para receber webhooks de mensagens do Telegram Bot API.
 * 
 * Endpoints:
 * - POST /api/webhooks/telegram - Recebe updates do Telegram
 * - GET /api/webhooks/telegram/health - Health check
 * 
 * Validação de autenticidade:
 * - Header: X-Telegram-Bot-Api-Secret-Token
 * - Valor: Secret token configurado ao registrar o webhook
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/telegram")
@RequiredArgsConstructor
public class TelegramIncomingWebhookController {

    private final TelegramWebhookService telegramWebhookService;

    /**
     * Health check endpoint.
     * 
     * @return Status OK
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "telegram-webhook"
        ));
    }

    /**
     * Recebe updates do Telegram Bot API.
     * 
     * O Telegram envia updates para este endpoint quando:
     * - Uma mensagem é recebida (texto, foto, documento, etc)
     * - Um botão inline é clicado (callback_query)
     * - Uma mensagem é editada
     * - Outros eventos do bot
     * 
     * Validação de autenticidade:
     * - O Telegram pode enviar um secret token no header X-Telegram-Bot-Api-Secret-Token
     * - Este token é configurado ao registrar o webhook via setWebhook
     * - Docs: https://core.telegram.org/bots/api#setwebhook
     * 
     * Formato do update:
     * {
     *   "update_id": 123456789,
     *   "message": {
     *     "message_id": 1,
     *     "from": { "id": 123, "first_name": "João" },
     *     "chat": { "id": 123, "type": "private" },
     *     "date": 1640000000,
     *     "text": "Olá!"
     *   }
     * }
     * 
     * @param secretToken Header X-Telegram-Bot-Api-Secret-Token (opcional)
     * @param update Corpo do webhook
     * @return 200 OK se processado com sucesso
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> handleUpdate(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody TelegramWebhookDto update) {

        log.info("Received Telegram update - updateId: {}, secretToken present: {}", 
                update.getUpdateId(), secretToken != null);

        // Validar secret token (se configurado)
        if (secretToken != null && !secretToken.isEmpty()) {
            if (!validateSecretToken(secretToken)) {
                log.error("Invalid Telegram secret token");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Invalid secret token"));
            }
            log.debug("Secret token validated successfully");
        } else {
            log.warn("No secret token provided - skipping validation (OK for mocks)");
        }

        try {
            // Determinar tipo de update
            String updateType = determineUpdateType(update);
            log.info("Processing Telegram update type: {}", updateType);

            // Processar update
            telegramWebhookService.processUpdate(update);
            
            log.info("Telegram update processed successfully");
            return ResponseEntity.ok(Map.of("ok", "true"));

        } catch (Exception e) {
            log.error("Error processing Telegram update", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process update: " + e.getMessage()));
        }
    }

    /**
     * Valida o secret token do Telegram.
     * 
     * O token é configurado ao registrar o webhook via setWebhook.
     * O Telegram envia este token em todas as requisições para validar a origem.
     * 
     * @param secretToken Token recebido no header
     * @return true se válido
     */
    private boolean validateSecretToken(String secretToken) {
        // TODO: Buscar do application.yml (webhook.telegram.secret-token)
        String expectedToken = "telegram-test-secret-key-12345";
        
        boolean isValid = expectedToken.equals(secretToken);
        
        if (!isValid) {
            log.warn("Secret token mismatch - Expected: {}, Got: {}", expectedToken, secretToken);
        }
        
        return isValid;
    }

    /**
     * Determina o tipo do update recebido.
     * 
     * Um update pode conter:
     * - message: Nova mensagem
     * - edited_message: Mensagem editada
     * - callback_query: Callback de botão inline
     * - etc (ver docs do Telegram)
     * 
     * @param update Update recebido
     * @return Tipo do update
     */
    private String determineUpdateType(TelegramWebhookDto update) {
        if (update.getMessage() != null) {
            // Determinar subtipo de mensagem
            TelegramWebhookDto.Message msg = update.getMessage();
            if (msg.getText() != null) return "message.text";
            if (msg.getPhoto() != null) return "message.photo";
            if (msg.getDocument() != null) return "message.document";
            return "message.other";
        }
        
        if (update.getEditedMessage() != null) {
            return "edited_message";
        }
        
        if (update.getCallbackQuery() != null) {
            return "callback_query";
        }
        
        return "unknown";
    }

    /**
     * Endpoint adicional para receber status de mensagens enviadas (opcional).
     * 
     * Este endpoint pode ser usado para rastrear status de mensagens enviadas
     * pelo bot (entregues, lidas, etc), similar ao WhatsApp.
     * 
     * Nota: O Telegram não envia confirmações de leitura por padrão,
     * mas pode enviar notificações de erro de envio.
     * 
     * @param callback Status callback
     * @return 200 OK
     */
    @PostMapping("/status")
    public ResponseEntity<Map<String, String>> handleStatusCallback(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody Map<String, Object> callback) {

        log.info("Received Telegram status callback: {}", callback);

        // Validar secret token
        if (secretToken != null && !secretToken.isEmpty()) {
            if (!validateSecretToken(secretToken)) {
                log.error("Invalid Telegram secret token");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Invalid secret token"));
            }
        }

        try {
            // TODO: Implementar processamento de status
            log.info("Telegram status callback processed (not implemented yet)");
            return ResponseEntity.ok(Map.of("ok", "true"));

        } catch (Exception e) {
            log.error("Error processing Telegram status callback", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
