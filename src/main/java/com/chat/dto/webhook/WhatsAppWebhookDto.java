package com.chat.dto.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO para webhooks recebidos do WhatsApp Business API
 * 
 * Documentação: https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks/components
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppWebhookDto {
    
    /**
     * Tipo de objeto, sempre "whatsapp_business_account"
     */
    private String object;
    
    /**
     * Lista de entradas do webhook
     */
    private List<Entry> entry;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        /**
         * ID da conta WhatsApp Business
         */
        private String id;
        
        /**
         * Lista de mudanças/eventos
         */
        private List<Change> changes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Change {
        /**
         * Tipo de campo alterado, ex: "messages"
         */
        private String field;
        
        /**
         * Valor contendo os detalhes do evento
         */
        private Value value;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Value {
        /**
         * Produto de mensageria, sempre "whatsapp"
         */
        @JsonProperty("messaging_product")
        private String messagingProduct;
        
        /**
         * Metadados da conta
         */
        private Metadata metadata;
        
        /**
         * Contatos (remetentes)
         */
        private List<Contact> contacts;
        
        /**
         * Mensagens recebidas
         */
        private List<Message> messages;
        
        /**
         * Status de mensagens enviadas
         */
        private List<Status> statuses;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        /**
         * Número de telefone exibido
         */
        @JsonProperty("display_phone_number")
        private String displayPhoneNumber;
        
        /**
         * ID do número de telefone
         */
        @JsonProperty("phone_number_id")
        private String phoneNumberId;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Contact {
        /**
         * Perfil do contato
         */
        private Profile profile;
        
        /**
         * ID do WhatsApp (número de telefone)
         */
        @JsonProperty("wa_id")
        private String waId;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Profile {
        /**
         * Nome do contato
         */
        private String name;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        /**
         * Número de telefone do remetente
         */
        private String from;
        
        /**
         * ID da mensagem no WhatsApp
         */
        private String id;
        
        /**
         * Timestamp Unix (segundos)
         */
        private String timestamp;
        
        /**
         * Tipo de mensagem: text, image, document, etc
         */
        private String type;
        
        /**
         * Conteúdo de texto (se type=text)
         */
        private Text text;
        
        /**
         * Conteúdo de imagem (se type=image)
         */
        private Image image;
        
        /**
         * Conteúdo de documento (se type=document)
         */
        private Document document;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Text {
        /**
         * Corpo da mensagem de texto
         */
        private String body;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Image {
        /**
         * ID da mídia
         */
        private String id;
        
        /**
         * Tipo MIME
         */
        @JsonProperty("mime_type")
        private String mimeType;
        
        /**
         * Hash SHA256
         */
        private String sha256;
        
        /**
         * Legenda (opcional)
         */
        private String caption;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Document {
        /**
         * ID da mídia
         */
        private String id;
        
        /**
         * Tipo MIME
         */
        @JsonProperty("mime_type")
        private String mimeType;
        
        /**
         * Nome do arquivo
         */
        private String filename;
        
        /**
         * Hash SHA256
         */
        private String sha256;
        
        /**
         * Legenda (opcional)
         */
        private String caption;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Status {
        /**
         * ID da mensagem
         */
        private String id;
        
        /**
         * Status: sent, delivered, read, failed
         */
        private String status;
        
        /**
         * Timestamp Unix (segundos)
         */
        private String timestamp;
        
        /**
         * ID do destinatário
         */
        @JsonProperty("recipient_id")
        private String recipientId;
        
        /**
         * Informações de conversação
         */
        private Conversation conversation;
        
        /**
         * Informações de preço
         */
        private Pricing pricing;
        
        /**
         * Erros (se status=failed)
         */
        private List<Error> errors;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Conversation {
        /**
         * ID da conversação
         */
        private String id;
        
        /**
         * Origem da conversação
         */
        private Origin origin;
        
        /**
         * Data de expiração
         */
        @JsonProperty("expiration_timestamp")
        private String expirationTimestamp;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Origin {
        /**
         * Tipo: user_initiated, business_initiated, referral_conversion
         */
        private String type;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pricing {
        /**
         * Se é cobrável
         */
        private Boolean billable;
        
        /**
         * Modelo de preço
         */
        @JsonProperty("pricing_model")
        private String pricingModel;
        
        /**
         * Categoria
         */
        private String category;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Error {
        /**
         * Código do erro
         */
        private Integer code;
        
        /**
         * Título do erro
         */
        private String title;
        
        /**
         * Mensagem do erro
         */
        private String message;
        
        /**
         * Detalhes adicionais
         */
        @JsonProperty("error_data")
        private ErrorData errorData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorData {
        /**
         * Detalhes do erro
         */
        private String details;
    }
}
