package com.chat.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import br.com.meuprojeto.chat.v1.Content;

@Document(collection = "chat_messages")
public class ChatMessage {

    @Id
    private String id;

    @Field("client_msg_id")
    private String clientMessageId;

    @Field("conversation_id")
    private String conversationId;

    @Field("sender_id")
    private String senderId;

    @Field("timestamp")
    private Instant timestamp;

    private Content content;

    public ChatMessage() {
    }

    // Construtor auxiliar (vamos criar um Mapper para isso depois)
    public ChatMessage(String clientMessageId, String conversationId, String senderId, Content content, Instant timestamp) {
        this.clientMessageId = clientMessageId;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.content = content;
        this.timestamp = timestamp;
    }

    public static class Content {
        private String text;

        //TODO: Adicionar campos de midia (mediaUrl, mimeType, etc)

        public Content(String text){
            this.text = text;
        }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

    }

    // Getters e Setters para os campos principais
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClientMessageId() { return clientMessageId; }
    public void setClientMessageId(String clientMessageId) { this.clientMessageId = clientMessageId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public Content getContent() { return content; }
    public void setContent(Content content) { this.content = content; }

}
