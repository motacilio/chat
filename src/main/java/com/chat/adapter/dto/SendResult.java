package com.chat.adapter.dto;

import java.time.Instant;

/**
 * RESPONSABILIDADE: Representa o resultado de envio de mensagem para plataforma externa.
 * 
 * NÃO RESPONSÁVEL POR: Persistir mensagem (ver MessageRepository),
 * gerenciar retry logic (ver circuit breaker), tracking de delivery status.
 * 
 * CONCEITO DE SISTEMAS DISTRIBUÍDOS:
 * Result Object Pattern - encapsula sucesso/falha com ID da mensagem na plataforma.
 * platformMessageId permite correlação com webhooks de status (delivered, read).
 * 
 * DESIGN EDUCACIONAL:
 * - success: indica se envio foi aceito pela plataforma
 * - platformMessageId: ID gerado pela plataforma (ex: Telegram message_id, WhatsApp wamid)
 * - errorMessage: detalhes técnicos da falha para debugging
 * - sentAt: timestamp do envio para métricas de latência
 * 
 * EXEMPLOS DE USO:
 * - Sucesso: SendResult.success("tg_123456789")
 * - Falha: SendResult.failure("Rate limit exceeded - retry after 30s")
 * 
 * @see com.chat.adapter.PlatformAdapter#sendMessage(String, String)
 * @see com.chat.adapter.PlatformAdapter#sendFile(String, com.chat.model.FileMetadata)
 */
public class SendResult {
    
    private final boolean success;
    private final String platformMessageId;  // null se falhou
    private final String errorCode;          // null se sucesso
    private final String errorMessage;       // null se sucesso
    private final Instant sentAt;
    
    private SendResult(boolean success, String platformMessageId, String errorCode, String errorMessage, Instant sentAt) {
        this.success = success;
        this.platformMessageId = platformMessageId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.sentAt = sentAt;
    }
    
    /**
     * Cria resultado de envio bem-sucedido.
     * 
     * @param platformMessageId ID da mensagem gerado pela plataforma externa
     * @return SendResult com success=true
     * 
     * FORMATOS DE ID POR PLATAFORMA:
     * - Telegram: numeric (ex: "123456789")
     * - WhatsApp: wamid format (ex: "wamid.HBgNNTUxMTk4...")
     * - Instagram: ig_mid format (ex: "ig_mid.123456789012345")
     */
    public static SendResult success(String platformMessageId) {
        return new SendResult(true, platformMessageId, null, null, Instant.now());
    }
    
    /**
     * Cria resultado de envio com falha (apenas errorMessage).
     * 
     * @param errorMessage Descrição técnica do erro para troubleshooting
     * @return SendResult com success=false
     * 
     * EXEMPLOS DE ERROS COMUNS:
     * - "User not found" - external_id inválido
     * - "Message too long" - excedeu limite de caracteres
     * - "Rate limit exceeded" - muitas requisições em curto período
     * - "API temporarily unavailable" - downtime da plataforma
     * - "Invalid token" - credenciais expiradas ou revogadas
     */
    public static SendResult failure(String errorMessage) {
        return new SendResult(false, null, null, errorMessage, Instant.now());
    }
    
    /**
     * Cria resultado de envio com falha (errorCode + errorMessage).
     * 
     * @param errorCode Código do erro padronizado (ex: "rate_limit_exceeded", "invalid_recipient")
     * @param errorMessage Descrição técnica detalhada do erro
     * @return SendResult com success=false
     * 
     * CÓDIGOS DE ERRO PADRONIZADOS (FR-039):
     * - "connection_timeout" - Timeout ao conectar com plataforma externa
     * - "rate_limit_exceeded" - API rate limit atingido
     * - "invalid_recipient" - external_id inválido ou formato incorreto
     * - "not_connected" - Adapter não está conectado
     * - "not_implemented" - Funcionalidade ainda não implementada
     */
    public static SendResult failure(String errorCode, String errorMessage) {
        return new SendResult(false, null, errorCode, errorMessage, Instant.now());
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getPlatformMessageId() {
        return platformMessageId;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public Instant getSentAt() {
        return sentAt;
    }
    
    /**
     * Timestamp when the send operation occurred.
     * Alias for getSentAt() to match test expectations.
     */
    public Instant getTimestamp() {
        return sentAt;
    }
    
    @Override
    public String toString() {
        if (success) {
            return String.format("SendResult{success=true, messageId='%s', sentAt=%s}",
                    platformMessageId, sentAt);
        } else {
            if (errorCode != null) {
                return String.format("SendResult{success=false, errorCode='%s', error='%s', sentAt=%s}",
                        errorCode, errorMessage, sentAt);
            } else {
                return String.format("SendResult{success=false, error='%s', sentAt=%s}",
                        errorMessage, sentAt);
            }
        }
    }
}
