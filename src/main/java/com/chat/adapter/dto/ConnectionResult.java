package com.chat.adapter.dto;

import java.time.Instant;

/**
 * RESPONSABILIDADE: Representa o resultado de uma tentativa de conexão com plataforma externa.
 * 
 * NÃO RESPONSÁVEL POR: Armazenar credenciais (ver PlatformCredentials),
 * gerenciar retry logic (ver PlatformRoutingService), persistir estado de conexão.
 * 
 * CONCEITO DE SISTEMAS DISTRIBUÍDOS:
 * Result Object Pattern - retorna sucesso/falha de forma explícita (sem exceptions).
 * Permite logging detalhado de cada tentativa de conexão para debugging.
 * 
 * DESIGN EDUCACIONAL:
 * - success: boolean flag para evitar null checking
 * - message: descrição human-readable do resultado
 * - timestamp: para tracking de latência e troubleshooting
 * 
 * EXEMPLOS DE USO:
 * - Sucesso: ConnectionResult.success("Telegram webhook registered at https://...")
 * - Falha: ConnectionResult.failure("WhatsApp token expired - refresh required")
 * 
 * @see com.chat.adapter.PlatformAdapter#connect(PlatformCredentials)
 */
public class ConnectionResult {
    
    private final boolean success;
    private final String message;
    private final Instant timestamp;
    
    private ConnectionResult(boolean success, String message, Instant timestamp) {
        this.success = success;
        this.message = message;
        this.timestamp = timestamp;
    }
    
    /**
     * Cria resultado de sucesso.
     * 
     * @param message Mensagem descritiva (ex: "Webhook registered successfully")
     * @return ConnectionResult com success=true
     */
    public static ConnectionResult success(String message) {
        return new ConnectionResult(true, message, Instant.now());
    }
    
    /**
     * Cria resultado de falha.
     * 
     * @param errorMessage Mensagem de erro detalhada para troubleshooting
     * @return ConnectionResult com success=false
     * 
     * EDGE CASES:
     * - Token inválido: "Invalid API token - check credentials"
     * - Webhook falhou: "Failed to register webhook: URL not accessible"
     * - Rate limit: "Too many connection attempts - retry after 60s"
     */
    public static ConnectionResult failure(String errorMessage) {
        return new ConnectionResult(false, errorMessage, Instant.now());
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return String.format("ConnectionResult{success=%s, message='%s', timestamp=%s}",
                success, message, timestamp);
    }
}
