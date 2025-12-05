package com.chat.dto.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO para webhooks recebidos do Telegram Bot API
 * 
 * Documentação: https://core.telegram.org/bots/api#update
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramWebhookDto {
    
    /**
     * ID único do update, sequencial
     */
    @JsonProperty("update_id")
    private Long updateId;
    
    /**
     * Mensagem nova recebida
     */
    private Message message;
    
    /**
     * Mensagem editada
     */
    @JsonProperty("edited_message")
    private Message editedMessage;
    
    /**
     * Callback query de botão inline
     */
    @JsonProperty("callback_query")
    private CallbackQuery callbackQuery;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        /**
         * ID único da mensagem
         */
        @JsonProperty("message_id")
        private Long messageId;
        
        /**
         * Remetente
         */
        private User from;
        
        /**
         * Chat onde a mensagem foi enviada
         */
        private Chat chat;
        
        /**
         * Data de envio (Unix timestamp)
         */
        private Long date;
        
        /**
         * Texto da mensagem
         */
        private String text;
        
        /**
         * Foto (array de tamanhos diferentes)
         */
        private List<PhotoSize> photo;
        
        /**
         * Documento
         */
        private Document document;
        
        /**
         * Legenda de mídia
         */
        private String caption;
        
        /**
         * Entidades (menções, hashtags, links)
         */
        private List<MessageEntity> entities;
        
        /**
         * Mensagem sendo respondida
         */
        @JsonProperty("reply_to_message")
        private Message replyToMessage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        /**
         * ID único do usuário
         */
        private Long id;
        
        /**
         * Se é bot
         */
        @JsonProperty("is_bot")
        private Boolean isBot;
        
        /**
         * Primeiro nome
         */
        @JsonProperty("first_name")
        private String firstName;
        
        /**
         * Sobrenome (opcional)
         */
        @JsonProperty("last_name")
        private String lastName;
        
        /**
         * Username (opcional)
         */
        private String username;
        
        /**
         * Código de idioma IETF
         */
        @JsonProperty("language_code")
        private String languageCode;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Chat {
        /**
         * ID único do chat
         */
        private Long id;
        
        /**
         * Tipo: private, group, supergroup, channel
         */
        private String type;
        
        /**
         * Título (para grupos/canais)
         */
        private String title;
        
        /**
         * Username (opcional)
         */
        private String username;
        
        /**
         * Primeiro nome (para chats privados)
         */
        @JsonProperty("first_name")
        private String firstName;
        
        /**
         * Sobrenome (para chats privados)
         */
        @JsonProperty("last_name")
        private String lastName;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhotoSize {
        /**
         * ID único do arquivo
         */
        @JsonProperty("file_id")
        private String fileId;
        
        /**
         * ID único que pode ser usado para download
         */
        @JsonProperty("file_unique_id")
        private String fileUniqueId;
        
        /**
         * Largura da foto
         */
        private Integer width;
        
        /**
         * Altura da foto
         */
        private Integer height;
        
        /**
         * Tamanho do arquivo em bytes
         */
        @JsonProperty("file_size")
        private Integer fileSize;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Document {
        /**
         * ID único do arquivo
         */
        @JsonProperty("file_id")
        private String fileId;
        
        /**
         * ID único que pode ser usado para download
         */
        @JsonProperty("file_unique_id")
        private String fileUniqueId;
        
        /**
         * Thumbnail (opcional)
         */
        private PhotoSize thumbnail;
        
        /**
         * Nome do arquivo
         */
        @JsonProperty("file_name")
        private String fileName;
        
        /**
         * Tipo MIME
         */
        @JsonProperty("mime_type")
        private String mimeType;
        
        /**
         * Tamanho do arquivo em bytes
         */
        @JsonProperty("file_size")
        private Long fileSize;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageEntity {
        /**
         * Tipo: mention, hashtag, url, bot_command, etc
         */
        private String type;
        
        /**
         * Offset no texto UTF-16
         */
        private Integer offset;
        
        /**
         * Comprimento da entidade
         */
        private Integer length;
        
        /**
         * URL (para links)
         */
        private String url;
        
        /**
         * Usuário (para menções)
         */
        private User user;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallbackQuery {
        /**
         * ID único do query
         */
        private String id;
        
        /**
         * Remetente
         */
        private User from;
        
        /**
         * Mensagem com o botão inline
         */
        private Message message;
        
        /**
         * ID global do chat inline
         */
        @JsonProperty("inline_message_id")
        private String inlineMessageId;
        
        /**
         * Identificador do chat
         */
        @JsonProperty("chat_instance")
        private String chatInstance;
        
        /**
         * Dados associados ao botão callback
         */
        private String data;
        
        /**
         * Nome curto do jogo (para game queries)
         */
        @JsonProperty("game_short_name")
        private String gameShortName;
    }
}
