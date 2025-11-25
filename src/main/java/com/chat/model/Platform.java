package com.chat.model;

/**
 * RESPONSABILIDADE: Define as plataformas de mensagens externas suportadas.
 * 
 * NÃO RESPONSÁVEL POR: Armazenar credenciais (ver PlatformCredentials),
 * gerenciar conexões (ver PlatformAdapter), validar external_id format (feito pelos adapters).
 * 
 * CONCEITO DE SISTEMAS DISTRIBUÍDOS:
 * Enum type-safe para evitar strings mágicas em integrações multi-plataforma.
 * Cada plataforma tem formato específico de external_id (phone vs username vs chatId).
 * 
 * DESIGN EDUCACIONAL:
 * - TELEGRAM: chatId numérico (ex: "123456789") - obtido via Bot API
 * - WHATSAPP: phone com código de país (ex: "+5511987654321") - E.164 format
 * - INSTAGRAM: username com @ (ex: "@john_doe") - Instagram handle
 * 
 * NOTAS DE IMPLEMENTAÇÃO:
 * - Mocks (WhatsApp, Instagram): não validam formato de external_id
 * - Real (Telegram - futuro): valida chatId via getChat API call
 * 
 * @see com.chat.adapter.PlatformAdapter
 * @see com.chat.model.LinkedAccount
 */
public enum Platform {
    
    /**
     * Telegram Bot API.
     * 
     * External ID format: Numeric chatId (ex: "123456789")
     * API: https://core.telegram.org/bots/api
     * Rate limits: 30 msgs/second per bot
     * 
     * IMPLEMENTAÇÃO: Real (T073 - futuro)
     */
    TELEGRAM,
    
    /**
     * WhatsApp Business API (Meta Cloud API).
     * 
     * External ID format: Phone number E.164 (ex: "+5511987654321")
     * API: https://developers.facebook.com/docs/whatsapp/cloud-api
     * Rate limits: 1000 msgs/24h (tier 1), 10000 msgs/24h (tier 2)
     * 
     * IMPLEMENTAÇÃO: Mock (T074)
     */
    WHATSAPP,
    
    /**
     * Instagram Messaging API (Facebook Graph API).
     * 
     * External ID format: Instagram username com @ (ex: "@john_doe")
     * API: https://developers.facebook.com/docs/messenger-platform
     * Limitações: Apenas mensagens 1:1 (sem grupos)
     * 
     * IMPLEMENTAÇÃO: Mock (T075)
     */
    INSTAGRAM;
    
    /**
     * Valida se external_id está no formato correto para a plataforma.
     * 
     * @param externalId ID do usuário na plataforma externa
     * @return true se formato válido, false caso contrário
     * 
     * NOTA: Implementação básica para educação.
     * Produção deveria usar regex patterns mais rigorosos.
     */
    public boolean isValidExternalId(String externalId) {
        if (externalId == null || externalId.isEmpty()) {
            return false;
        }
        
        switch (this) {
            case TELEGRAM:
                // chatId é numérico
                return externalId.matches("\\d+");
                
            case WHATSAPP:
                // Phone number E.164: + seguido de dígitos
                return externalId.matches("\\+\\d{10,15}");
                
            case INSTAGRAM:
                // Username com @
                return externalId.startsWith("@") && externalId.length() > 1;
                
            default:
                return false;
        }
    }
    
    /**
     * Retorna exemplo de external_id válido para documentação.
     */
    public String getExampleExternalId() {
        switch (this) {
            case TELEGRAM:
                return "123456789";
            case WHATSAPP:
                return "+5511987654321";
            case INSTAGRAM:
                return "@john_doe";
            default:
                return "";
        }
    }
}
