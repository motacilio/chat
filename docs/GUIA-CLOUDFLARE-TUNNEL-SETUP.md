# Guia: Configurar Cloudflare Tunnel para Webhooks

## ✅ Cloudflare Tunnel Instalado

**Status**: Instalado com sucesso via winget  
**Versão**: 2025.8.1  
**Comando disponível**: `cloudflared`

---

## 🎯 Objetivo

Expor o servidor chat (porta 8081) para a internet via Cloudflare Tunnel, permitindo que WhatsApp/Telegram/Instagram enviem webhooks mesmo estando atrás de firewall/NAT.

---

## 📋 Pré-requisitos

1. ✅ **Cloudflare Tunnel instalado** (já feito)
2. ⚠️ **Conta Cloudflare gratuita** (criar em https://dash.cloudflare.com)
3. ⚠️ **Domínio próprio** (opcional mas recomendado)
   - Se não tiver: Cloudflare pode fornecer domínio `.trycloudflare.com` temporário

---

## 🚀 Setup Rápido (Sem Domínio Próprio)

### **Opção 1: Túnel Temporário (Teste Rápido)**

```powershell
# Iniciar túnel temporário (sem login necessário)
cloudflared tunnel --url http://localhost:8081
```

**Saída**:
```
Your quick Tunnel has been created! Visit it at:
https://random-name-1234.trycloudflare.com
```

**Vantagens**:
- ✅ Zero configuração
- ✅ Funciona imediatamente
- ✅ Sem necessidade de conta Cloudflare

**Desvantagens**:
- ❌ URL muda a cada reinício
- ❌ Não persiste entre sessões
- ❌ Apenas para testes rápidos

**Teste**:
```powershell
# Em outro terminal, verificar se está acessível
curl https://random-name-1234.trycloudflare.com/actuator/health
```

---

## 🔧 Setup Produção (Com Conta Cloudflare)

### **Passo 1: Login no Cloudflare**

```powershell
# Abrir navegador para autenticar
cloudflared tunnel login
```

**O que acontece**:
1. Navegador abre automaticamente
2. Fazer login na conta Cloudflare
3. Autorizar cloudflared
4. Arquivo de credenciais salvo em: `C:\Users\marcos.pereira\.cloudflared\cert.pem`

**Saída esperada**:
```
You have successfully logged in.
If you wish to copy your credentials to a server, they have been saved to:
C:\Users\marcos.pereira\.cloudflared\cert.pem
```

---

### **Passo 2: Criar Túnel Persistente**

```powershell
# Criar túnel nomeado
cloudflared tunnel create chat-webhook-tunnel
```

**Saída**:
```
Tunnel credentials written to C:\Users\marcos.pereira\.cloudflared\{TUNNEL_ID}.json
Created tunnel chat-webhook-tunnel with id {TUNNEL_ID}
```

**Copiar o TUNNEL_ID** da saída (exemplo: `a1b2c3d4-e5f6-7890-abcd-ef1234567890`)

---

### **Passo 3: Configurar Rota DNS** (Se tiver domínio próprio)

```powershell
# Substituir 'seudominio.com' pelo seu domínio real
cloudflared tunnel route dns chat-webhook-tunnel webhook.seudominio.com
```

**Saída**:
```
Created route for webhook.seudominio.com to tunnel chat-webhook-tunnel
```

**Se NÃO tiver domínio**, pule este passo e use URL `.trycloudflare.com`

---

### **Passo 4: Criar Arquivo de Configuração**

```powershell
# Criar diretório de configuração (se não existir)
New-Item -ItemType Directory -Force -Path "$env:USERPROFILE\.cloudflared"

# Criar arquivo de configuração
@"
tunnel: chat-webhook-tunnel
credentials-file: C:\Users\marcos.pereira\.cloudflared\{TUNNEL_ID}.json

ingress:
  # Rota principal - todo tráfego vai para aplicação chat
  - hostname: webhook.seudominio.com
    service: http://localhost:8081
  
  # Rota padrão (catch-all) - obrigatório
  - service: http_status:404
"@ | Out-File -FilePath "$env:USERPROFILE\.cloudflared\config.yml" -Encoding UTF8
```

**⚠️ IMPORTANTE**: Substituir:
- `{TUNNEL_ID}` pelo ID real do túnel (da etapa 2)
- `webhook.seudominio.com` pelo seu domínio (ou remover linha `hostname` se usar `.trycloudflare.com`)

**Se não tiver domínio**, use esta configuração:

```powershell
@"
tunnel: chat-webhook-tunnel
credentials-file: C:\Users\marcos.pereira\.cloudflared\{TUNNEL_ID}.json

ingress:
  - service: http://localhost:8081
"@ | Out-File -FilePath "$env:USERPROFILE\.cloudflared\config.yml" -Encoding UTF8
```

---

### **Passo 5: Iniciar Túnel**

```powershell
# Iniciar túnel (em janela separada para ver logs)
cloudflared tunnel run chat-webhook-tunnel
```

**Saída esperada**:
```
INF Starting tunnel tunnelID={TUNNEL_ID}
INF Connection registered connIndex=0 location=GRU
INF Connection registered connIndex=1 location=GIG
INF Registered tunnel connection
```

**Testar**:
```powershell
# Em outro terminal
curl https://webhook.seudominio.com/actuator/health

# Ou se não tiver domínio, verificar logs para URL .trycloudflare.com
```

---

### **Passo 6: Rodar como Serviço Windows** (Opcional - Produção)

Para manter o túnel rodando mesmo após reiniciar o PC:

```powershell
# Instalar como serviço Windows
cloudflared service install

# Iniciar serviço
Start-Service cloudflared

# Verificar status
Get-Service cloudflared
```

**Vantagens**:
- ✅ Túnel inicia automaticamente com Windows
- ✅ Roda em background
- ✅ Reinicia automaticamente se falhar

**Parar serviço**:
```powershell
Stop-Service cloudflared
```

**Desinstalar serviço**:
```powershell
cloudflared service uninstall
```

---

## 📝 Configurar Webhook no WhatsApp

Após túnel configurado:

```powershell
# Configurar webhook no WhatsApp Business API
curl -X POST "https://graph.facebook.com/v18.0/{PHONE_NUMBER_ID}/subscribed_apps" `
  -H "Authorization: Bearer {WHATSAPP_ACCESS_TOKEN}" `
  -H "Content-Type: application/json" `
  -d '{
    "callback_url": "https://webhook.seudominio.com/api/webhooks/whatsapp",
    "verify_token": "meu-token-secreto-123",
    "fields": ["messages", "message_status"]
  }'
```

**Substituir**:
- `{PHONE_NUMBER_ID}` - ID do telefone WhatsApp Business
- `{WHATSAPP_ACCESS_TOKEN}` - Token de acesso da API
- `webhook.seudominio.com` - Seu domínio (ou URL `.trycloudflare.com`)

---

## 🧪 Testar Webhook

### **Teste 1: Verificação do WhatsApp**

O WhatsApp envia um GET request para verificar o webhook:

```http
GET https://webhook.seudominio.com/api/webhooks/whatsapp?hub.mode=subscribe&hub.challenge=12345&hub.verify_token=meu-token-secreto-123
```

**Seu servidor deve responder**:
```http
200 OK
Content-Type: text/plain

12345
```

### **Teste 2: Enviar Mensagem de Teste**

Envie mensagem para número WhatsApp Business → Webhook deve chamar seu servidor:

```http
POST https://webhook.seudominio.com/api/webhooks/whatsapp
Content-Type: application/json

{
  "entry": [{
    "changes": [{
      "value": {
        "messages": [{
          "from": "+5511999999999",
          "text": {"body": "Teste"}
        }]
      }
    }]
  }]
}
```

**Verificar logs**:
```powershell
# Logs do túnel Cloudflare
cloudflared tunnel logs

# Logs da aplicação chat
# Verificar terminal onde está rodando o servidor
```

---

## 📊 Monitoramento

### **Dashboard Cloudflare**

Acesse: https://dash.cloudflare.com → Zero Trust → Access → Tunnels

**Métricas disponíveis**:
- 📈 Requests/segundo
- 🌍 Origem geográfica
- ⏱️ Latência
- 🚫 Requests bloqueados

### **Logs em Tempo Real**

```powershell
# Logs do túnel
cloudflared tunnel logs chat-webhook-tunnel

# Seguir logs em tempo real
cloudflared tunnel logs chat-webhook-tunnel --follow
```

---

## 🔒 Segurança

### **1. Validar Webhook do WhatsApp**

No `WebhookController.java`, adicione validação HMAC:

```java
@PostMapping("/whatsapp")
public ResponseEntity<String> whatsappWebhook(
        @RequestBody String payload,
        @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
    
    // Validar assinatura HMAC
    if (!validateWhatsAppSignature(payload, signature)) {
        logger.warn("Invalid WhatsApp webhook signature");
        return ResponseEntity.status(403).body("Invalid signature");
    }
    
    // Processar webhook
    // ...
}

private boolean validateWhatsAppSignature(String payload, String signature) {
    if (signature == null) {
        return false;
    }
    
    String appSecret = System.getenv("WHATSAPP_APP_SECRET");
    String expectedSignature = "sha256=" + hmacSha256(payload, appSecret);
    
    return signature.equals(expectedSignature);
}
```

### **2. Limitar IPs (Cloudflare Firewall)**

Dashboard Cloudflare → Security → WAF → Custom rules:

```
Rule: Allow WhatsApp IPs only
Expression: ip.src in {157.240.0.0/16 31.13.0.0/16 ...}
Action: Allow
```

### **3. Rate Limiting**

```powershell
# Configurar rate limit no config.yml
@"
tunnel: chat-webhook-tunnel
credentials-file: C:\Users\marcos.pereira\.cloudflared\{TUNNEL_ID}.json

ingress:
  - hostname: webhook.seudominio.com
    service: http://localhost:8081
    originRequest:
      # Timeout de conexão
      connectTimeout: 30s
      # Timeout de request
      noTLSVerify: false
"@ | Out-File -FilePath "$env:USERPROFILE\.cloudflared\config.yml" -Encoding UTF8
```

---

## 🛠️ Troubleshooting

### **Problema 1: "tunnel not found"**

```powershell
# Listar túneis existentes
cloudflared tunnel list

# Criar novo túnel se não existir
cloudflared tunnel create chat-webhook-tunnel
```

### **Problema 2: "connection refused"**

```powershell
# Verificar se aplicação está rodando
curl http://localhost:8081/actuator/health

# Verificar porta correta no config.yml
Get-Content "$env:USERPROFILE\.cloudflared\config.yml"
```

### **Problema 3: "certificate not found"**

```powershell
# Re-autenticar
cloudflared tunnel login

# Verificar arquivo cert.pem existe
Test-Path "$env:USERPROFILE\.cloudflared\cert.pem"
```

### **Problema 4: URL .trycloudflare.com não funciona**

```powershell
# Parar túnel temporário
# Ctrl+C no terminal

# Criar túnel nomeado persistente
cloudflared tunnel create chat-webhook-tunnel

# Obter URL pública
cloudflared tunnel info chat-webhook-tunnel
```

---

## 📚 Comandos Úteis

```powershell
# Listar túneis
cloudflared tunnel list

# Informações do túnel
cloudflared tunnel info chat-webhook-tunnel

# Deletar túnel
cloudflared tunnel delete chat-webhook-tunnel

# Verificar configuração
cloudflared tunnel ingress validate

# Testar rota
cloudflared tunnel ingress rule https://webhook.seudominio.com

# Logs em tempo real
cloudflared tunnel logs chat-webhook-tunnel --follow
```

---

## ✅ Checklist de Setup

- [ ] Cloudflared instalado
- [ ] Login no Cloudflare (`cloudflared tunnel login`)
- [ ] Túnel criado (`cloudflared tunnel create`)
- [ ] Arquivo config.yml criado
- [ ] Túnel iniciado (`cloudflared tunnel run`)
- [ ] URL pública acessível (teste com curl)
- [ ] Webhook configurado no WhatsApp
- [ ] Validação HMAC implementada
- [ ] Teste de webhook realizado
- [ ] Serviço Windows configurado (opcional)

---

## 🎯 Próximos Passos

1. **Iniciar túnel**:
```powershell
cloudflared tunnel run chat-webhook-tunnel
```

2. **Testar acesso**:
```powershell
curl https://webhook.seudominio.com/actuator/health
```

3. **Configurar webhook no WhatsApp**

4. **Enviar mensagem de teste**

5. **Verificar logs** para confirmar recebimento

---

## 💡 Dicas

- **Desenvolvimento**: Use túnel temporário (`cloudflared tunnel --url http://localhost:8081`)
- **Staging**: Use túnel nomeado com domínio `.trycloudflare.com`
- **Produção**: Use túnel nomeado com domínio próprio + serviço Windows

**URL Temporária** vs **URL Fixa**:
- Temporária: Muda a cada reinício (dev/teste)
- Fixa: Permanente (produção)

**Custo**: **GRÁTIS** até 50GB/mês de tráfego
