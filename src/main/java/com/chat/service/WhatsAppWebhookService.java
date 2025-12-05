package com.chat.service;

import com.chat.dto.webhook.WhatsAppWebhookDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Serviço para processar webhooks recebidos do WhatsApp Business API.
 * 
 * Responsabilidades:
 * - Parsear payload JSON do WhatsApp
 * - Extrair mensagens recebidas
 * - Publicar no tópico Kafka apropriado
 * - Logs e monitoramento
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppWebhookService {

    private final KafkaTemplate<String, String> jsonKafkaTemplate;
    private final ObjectMapper objectMapper;

    // Tópico Kafka para mensagens recebidas do WhatsApp
    private static final String WHATSAPP_INCOMING_MESSAGES_TOPIC = "whatsapp-incoming-messages";

    /**
     * Processa webhook recebido do WhatsApp.
     * 
     * Fluxo:
     * 1. Parse do JSON
     * 2. Extração de mensagens
     * 3. Publicação no Kafka
     * 4. Log de auditoria
     * 
     * @param payload JSON do webhook
     */
    public void processIncomingWebhook(String payload) {
        try {
            // Parse do payload
            WhatsAppWebhookDto webhook = objectMapper.readValue(payload, WhatsAppWebhookDto.class);
            
            log.info("Parsed WhatsApp webhook - object: {}, entries: {}", 
                    webhook.getObject(), 
                    webhook.getEntry() != null ? webhook.getEntry().size() : 0);

            // Processar cada entrada
            if (webhook.getEntry() != null) {
                for (WhatsAppWebhookDto.Entry entry : webhook.getEntry()) {
                    processEntry(entry);
                }
            }

        } catch (Exception e) {
            log.error("Error parsing WhatsApp webhook payload", e);
            throw new RuntimeException("Failed to parse webhook", e);
        }
    }

    /**
     * Processa uma entrada do webhook.
     * 
     * @param entry Entrada contendo mudanças/eventos
     */
    private void processEntry(WhatsAppWebhookDto.Entry entry) {
        log.debug("Processing WhatsApp entry - id: {}, changes: {}", 
                entry.getId(), 
                entry.getChanges() != null ? entry.getChanges().size() : 0);

        if (entry.getChanges() == null) {
            return;
        }

        for (WhatsAppWebhookDto.Change change : entry.getChanges()) {
            processChange(change);
        }
    }

    /**
     * Processa uma mudança/evento.
     * 
     * @param change Mudança contendo mensagens ou status
     */
    private void processChange(WhatsAppWebhookDto.Change change) {
        log.debug("Processing WhatsApp change - field: {}", change.getField());

        if (change.getValue() == null) {
            return;
        }

        WhatsAppWebhookDto.Value value = change.getValue();

        // Processar mensagens recebidas
        if (value.getMessages() != null && !value.getMessages().isEmpty()) {
            for (WhatsAppWebhookDto.Message message : value.getMessages()) {
                processIncomingMessage(message, value);
            }
        }

        // Ignorar status updates aqui (são tratados pelo WebhookController)
        if (value.getStatuses() != null && !value.getStatuses().isEmpty()) {
            log.debug("Ignoring status updates in incoming webhook (handled by WebhookController)");
        }
    }

    /**
     * Processa mensagem recebida e publica no Kafka.
     * 
     * @param message Mensagem recebida
     * @param value Valor contendo metadados
     */
    private void processIncomingMessage(WhatsAppWebhookDto.Message message, WhatsAppWebhookDto.Value value) {
        try {
            log.info("Processing incoming WhatsApp message - id: {}, from: {}, type: {}", 
                    message.getId(), message.getFrom(), message.getType());

            // Construir evento para Kafka
            Map<String, Object> event = new HashMap<>();
            event.put("platform", "WHATSAPP");
            event.put("platformMessageId", message.getId());
            event.put("from", message.getFrom());
            event.put("type", message.getType());
            event.put("timestamp", message.getTimestamp());
            event.put("receivedAt", Instant.now().toString());

            // Adicionar metadados
            if (value.getMetadata() != null) {
                event.put("phoneNumberId", value.getMetadata().getPhoneNumberId());
                event.put("displayPhoneNumber", value.getMetadata().getDisplayPhoneNumber());
            }

            // Adicionar conteúdo baseado no tipo
            switch (message.getType()) {
                case "text":
                    if (message.getText() != null) {
                        event.put("content", message.getText().getBody());
                    }
                    break;

                case "image":
                    if (message.getImage() != null) {
                        Map<String, Object> imageData = new HashMap<>();
                        imageData.put("id", message.getImage().getId());
                        imageData.put("mimeType", message.getImage().getMimeType());
                        imageData.put("sha256", message.getImage().getSha256());
                        imageData.put("caption", message.getImage().getCaption());
                        event.put("media", imageData);
                    }
                    break;

                case "document":
                    if (message.getDocument() != null) {
                        Map<String, Object> docData = new HashMap<>();
                        docData.put("id", message.getDocument().getId());
                        docData.put("filename", message.getDocument().getFilename());
                        docData.put("mimeType", message.getDocument().getMimeType());
                        docData.put("sha256", message.getDocument().getSha256());
                        docData.put("caption", message.getDocument().getCaption());
                        event.put("media", docData);
                    }
                    break;

                default:
                    log.warn("Unsupported message type: {}", message.getType());
                    event.put("content", "Unsupported message type: " + message.getType());
            }

            // Publicar no Kafka
            String eventJson = objectMapper.writeValueAsString(event);
            jsonKafkaTemplate.send(WHATSAPP_INCOMING_MESSAGES_TOPIC, message.getId(), eventJson);

            log.info("Published WhatsApp incoming message to Kafka - topic: {}, messageId: {}", 
                    WHATSAPP_INCOMING_MESSAGES_TOPIC, message.getId());

        } catch (Exception e) {
            log.error("Error processing incoming WhatsApp message", e);
            throw new RuntimeException("Failed to process message", e);
        }
    }
}
