package com.chat.service;

import com.chat.dto.webhook.TelegramWebhookDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Serviço para processar updates recebidos do Telegram Bot API.
 * 
 * Responsabilidades:
 * - Processar diferentes tipos de updates (mensagens, callbacks, etc)
 * - Publicar eventos no Kafka
 * - Logs e monitoramento
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramWebhookService {

    private final KafkaTemplate<String, String> jsonKafkaTemplate;
    private final ObjectMapper objectMapper;

    // Tópico Kafka para mensagens recebidas do Telegram
    private static final String TELEGRAM_INCOMING_MESSAGES_TOPIC = "telegram-incoming-messages";
    
    // Tópico Kafka para callback queries (botões inline)
    private static final String TELEGRAM_CALLBACK_QUERIES_TOPIC = "telegram-callback-queries";

    /**
     * Processa update recebido do Telegram.
     * 
     * Um update pode conter:
     * - message: Nova mensagem
     * - edited_message: Mensagem editada
     * - callback_query: Callback de botão inline
     * - etc
     * 
     * @param update Update do Telegram
     */
    public void processUpdate(TelegramWebhookDto update) {
        try {
            log.info("Processing Telegram update - updateId: {}", update.getUpdateId());

            // Processar mensagem nova
            if (update.getMessage() != null) {
                processMessage(update.getMessage(), update.getUpdateId());
            }

            // Processar mensagem editada
            else if (update.getEditedMessage() != null) {
                processEditedMessage(update.getEditedMessage(), update.getUpdateId());
            }

            // Processar callback query (botão inline)
            else if (update.getCallbackQuery() != null) {
                processCallbackQuery(update.getCallbackQuery(), update.getUpdateId());
            }

            // Outros tipos de update
            else {
                log.warn("Unsupported Telegram update type - updateId: {}", update.getUpdateId());
            }

        } catch (Exception e) {
            log.error("Error processing Telegram update", e);
            throw new RuntimeException("Failed to process update", e);
        }
    }

    /**
     * Processa mensagem nova e publica no Kafka.
     * 
     * @param message Mensagem recebida
     * @param updateId ID do update
     */
    private void processMessage(TelegramWebhookDto.Message message, Long updateId) {
        try {
            log.info("Processing Telegram message - messageId: {}, chatId: {}, from: {}", 
                    message.getMessageId(), 
                    message.getChat() != null ? message.getChat().getId() : null,
                    message.getFrom() != null ? message.getFrom().getId() : null);

            // Construir evento para Kafka
            Map<String, Object> event = new HashMap<>();
            event.put("platform", "TELEGRAM");
            event.put("updateId", updateId);
            event.put("messageId", message.getMessageId());
            event.put("date", message.getDate());
            event.put("receivedAt", Instant.now().toString());

            // Adicionar dados do remetente
            if (message.getFrom() != null) {
                Map<String, Object> fromData = new HashMap<>();
                fromData.put("id", message.getFrom().getId());
                fromData.put("firstName", message.getFrom().getFirstName());
                fromData.put("lastName", message.getFrom().getLastName());
                fromData.put("username", message.getFrom().getUsername());
                fromData.put("isBot", message.getFrom().getIsBot());
                event.put("from", fromData);
            }

            // Adicionar dados do chat
            if (message.getChat() != null) {
                Map<String, Object> chatData = new HashMap<>();
                chatData.put("id", message.getChat().getId());
                chatData.put("type", message.getChat().getType());
                chatData.put("title", message.getChat().getTitle());
                chatData.put("username", message.getChat().getUsername());
                event.put("chat", chatData);
            }

            // Determinar tipo de mensagem e adicionar conteúdo
            String messageType = determineMessageType(message);
            event.put("type", messageType);

            switch (messageType) {
                case "text":
                    event.put("content", message.getText());
                    
                    // Adicionar entidades (menções, hashtags, links)
                    if (message.getEntities() != null && !message.getEntities().isEmpty()) {
                        event.put("hasEntities", true);
                        event.put("entitiesCount", message.getEntities().size());
                    }
                    break;

                case "photo":
                    if (message.getPhoto() != null && !message.getPhoto().isEmpty()) {
                        // Pegar a maior foto (última da lista)
                        TelegramWebhookDto.PhotoSize largestPhoto = 
                                message.getPhoto().get(message.getPhoto().size() - 1);
                        
                        Map<String, Object> photoData = new HashMap<>();
                        photoData.put("fileId", largestPhoto.getFileId());
                        photoData.put("fileUniqueId", largestPhoto.getFileUniqueId());
                        photoData.put("width", largestPhoto.getWidth());
                        photoData.put("height", largestPhoto.getHeight());
                        photoData.put("fileSize", largestPhoto.getFileSize());
                        
                        event.put("media", photoData);
                        
                        if (message.getCaption() != null) {
                            event.put("caption", message.getCaption());
                        }
                    }
                    break;

                case "document":
                    if (message.getDocument() != null) {
                        Map<String, Object> docData = new HashMap<>();
                        docData.put("fileId", message.getDocument().getFileId());
                        docData.put("fileUniqueId", message.getDocument().getFileUniqueId());
                        docData.put("fileName", message.getDocument().getFileName());
                        docData.put("mimeType", message.getDocument().getMimeType());
                        docData.put("fileSize", message.getDocument().getFileSize());
                        
                        event.put("media", docData);
                        
                        if (message.getCaption() != null) {
                            event.put("caption", message.getCaption());
                        }
                    }
                    break;

                default:
                    event.put("content", "Unsupported message type: " + messageType);
            }

            // Adicionar reply info se for resposta
            if (message.getReplyToMessage() != null) {
                event.put("isReply", true);
                event.put("replyToMessageId", message.getReplyToMessage().getMessageId());
            }

            // Publicar no Kafka
            String eventJson = objectMapper.writeValueAsString(event);
            String key = message.getChat().getId() + ":" + message.getMessageId();
            jsonKafkaTemplate.send(TELEGRAM_INCOMING_MESSAGES_TOPIC, key, eventJson);

            log.info("Published Telegram message to Kafka - topic: {}, messageId: {}", 
                    TELEGRAM_INCOMING_MESSAGES_TOPIC, message.getMessageId());

        } catch (Exception e) {
            log.error("Error processing Telegram message", e);
            throw new RuntimeException("Failed to process message", e);
        }
    }

    /**
     * Processa mensagem editada.
     * 
     * @param message Mensagem editada
     * @param updateId ID do update
     */
    private void processEditedMessage(TelegramWebhookDto.Message message, Long updateId) {
        log.info("Processing edited Telegram message - messageId: {}", message.getMessageId());
        
        // Processar como mensagem normal, mas marcar como editada
        processMessage(message, updateId);
        
        // TODO: Implementar lógica específica para edição
        // - Atualizar mensagem existente no MongoDB
        // - Notificar usuários via WebSocket
    }

    /**
     * Processa callback query (clique em botão inline).
     * 
     * @param callbackQuery Callback query
     * @param updateId ID do update
     */
    private void processCallbackQuery(TelegramWebhookDto.CallbackQuery callbackQuery, Long updateId) {
        try {
            log.info("Processing Telegram callback query - id: {}, data: {}", 
                    callbackQuery.getId(), callbackQuery.getData());

            // Construir evento para Kafka
            Map<String, Object> event = new HashMap<>();
            event.put("platform", "TELEGRAM");
            event.put("updateId", updateId);
            event.put("callbackQueryId", callbackQuery.getId());
            event.put("data", callbackQuery.getData());
            event.put("chatInstance", callbackQuery.getChatInstance());
            event.put("receivedAt", Instant.now().toString());

            // Adicionar dados do usuário
            if (callbackQuery.getFrom() != null) {
                Map<String, Object> fromData = new HashMap<>();
                fromData.put("id", callbackQuery.getFrom().getId());
                fromData.put("firstName", callbackQuery.getFrom().getFirstName());
                fromData.put("username", callbackQuery.getFrom().getUsername());
                event.put("from", fromData);
            }

            // Adicionar dados da mensagem original
            if (callbackQuery.getMessage() != null) {
                event.put("messageId", callbackQuery.getMessage().getMessageId());
                if (callbackQuery.getMessage().getChat() != null) {
                    event.put("chatId", callbackQuery.getMessage().getChat().getId());
                }
            }

            // Publicar no Kafka
            String eventJson = objectMapper.writeValueAsString(event);
            jsonKafkaTemplate.send(TELEGRAM_CALLBACK_QUERIES_TOPIC, callbackQuery.getId(), eventJson);

            log.info("Published Telegram callback query to Kafka - topic: {}, callbackQueryId: {}", 
                    TELEGRAM_CALLBACK_QUERIES_TOPIC, callbackQuery.getId());

        } catch (Exception e) {
            log.error("Error processing Telegram callback query", e);
            throw new RuntimeException("Failed to process callback query", e);
        }
    }

    /**
     * Determina o tipo de mensagem.
     * 
     * @param message Mensagem
     * @return Tipo da mensagem
     */
    private String determineMessageType(TelegramWebhookDto.Message message) {
        if (message.getText() != null) return "text";
        if (message.getPhoto() != null) return "photo";
        if (message.getDocument() != null) return "document";
        // TODO: Adicionar outros tipos: audio, video, sticker, location, contact, etc
        return "unknown";
    }
}
