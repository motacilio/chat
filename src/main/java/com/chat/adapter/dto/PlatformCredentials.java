package com.chat.adapter.dto;

/**
 * RESPONSABILIDADE: Armazena credenciais de acesso a plataformas externas.
 * 
 * NÃO RESPONSÁVEL POR: Validar credenciais (feito pelo adapter),
 * criptografar secrets (ver application.properties encryption),
 * renovar tokens expirados (ver refresh token logic - futuro).
 * 
 * CONCEITO DE SISTEMAS DISTRIBUÍDOS:
 * Credentials Object - encapsula autenticação específica de cada plataforma.
 * Evita passar múltiplos parâmetros string (anti-pattern).
 * 
 * DESIGN EDUCACIONAL:
 * - platform: identifica qual plataforma (TELEGRAM, WHATSAPP, INSTAGRAM)
 * - apiToken: chave de autenticação (Bot Token, Bearer Token, Page Access Token)
 * - apiSecret: secret key para validação de webhooks (opcional)
 * - webhookUrl: endpoint onde plataforma enviará callbacks
 * 
 * ESTRUTURA POR PLATAFORMA:
 * 
 * TELEGRAM:
 * - apiToken: Bot Token (ex: "1234567890:ABCdef...") - obtido via @BotFather
 * - apiSecret: X-Telegram-Bot-Api-Secret-Token (opcional, para webhook validation)
 * - webhookUrl: https://api.example.com/webhooks/telegram
 * 
 * WHATSAPP:
 * - apiToken: WhatsApp Business Account Bearer Token
 * - apiSecret: App Secret (para webhook validation)
 * - webhookUrl: https://api.example.com/webhooks/whatsapp
 * 
 * INSTAGRAM:
 * - apiToken: Page Access Token (Facebook Graph API)
 * - apiSecret: App Secret
 * - webhookUrl: https://api.example.com/webhooks/instagram
 * 
 * SEGURANÇA - POC vs PRODUÇÃO:
 * POC (atual): Secrets em application.properties (NÃO RECOMENDADO)
 * Produção: Usar environment variables + secret manager (AWS Secrets Manager, HashiCorp Vault)
 * 
 * @see com.chat.adapter.PlatformAdapter#connect(PlatformCredentials)
 */
public class PlatformCredentials {
    
    private final String platform;   // TELEGRAM, WHATSAPP, INSTAGRAM
    private final String apiToken;
    private final String apiSecret;  // Opcional - usado para webhook validation
    private final String webhookUrl; // Opcional - onde plataforma enviará callbacks
    
    private PlatformCredentials(String platform, String apiToken, String apiSecret, String webhookUrl) {
        this.platform = platform;
        this.apiToken = apiToken;
        this.apiSecret = apiSecret;
        this.webhookUrl = webhookUrl;
    }
    
    /**
     * Builder pattern para criação de credenciais.
     * Permite campos opcionais (apiSecret, webhookUrl).
     */
    public static class Builder {
        private final String platform;
        private final String apiToken;
        private String apiSecret;
        private String webhookUrl;
        
        public Builder(String platform, String apiToken) {
            this.platform = platform;
            this.apiToken = apiToken;
        }
        
        public Builder withApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
            return this;
        }
        
        public Builder withWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
            return this;
        }
        
        public PlatformCredentials build() {
            return new PlatformCredentials(platform, apiToken, apiSecret, webhookUrl);
        }
    }
    
    public String getPlatform() {
        return platform;
    }
    
    public String getApiToken() {
        return apiToken;
    }
    
    public String getApiSecret() {
        return apiSecret;
    }
    
    public String getWebhookUrl() {
        return webhookUrl;
    }
    
    @Override
    public String toString() {
        // SEGURANÇA: Nunca loga tokens completos
        String maskedToken = apiToken != null && apiToken.length() > 10
                ? apiToken.substring(0, 10) + "..."
                : "***";
        
        return String.format("PlatformCredentials{platform='%s', token='%s', hasSecret=%s, webhookUrl='%s'}",
                platform, maskedToken, (apiSecret != null), webhookUrl);
    }
}
