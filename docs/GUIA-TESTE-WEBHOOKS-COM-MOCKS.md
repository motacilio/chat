# Guia de Testes com Mocks e Cloudflare Tunnel

Este guia mostra como testar webhooks do WhatsApp e Telegram **localmente** usando mocks, **sem precisar das APIs reais**.

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Pré-requisitos](#pré-requisitos)
3. [Cenário 1: Teste Local (sem Cloudflare)](#cenário-1-teste-local-sem-cloudflare)
4. [Cenário 2: Teste com Cloudflare Tunnel](#cenário-2-teste-com-cloudflare-tunnel)
5. [Cenário 3: Mock Contínuo (Simulador de Tráfego)](#cenário-3-mock-contínuo-simulador-de-tráfego)
6. [Validação e Troubleshooting](#validação-e-troubleshooting)

---

## 🎯 Visão Geral

```
┌─────────────────────────────────────────────────────────┐
│  Ambiente de Desenvolvimento Local                      │
│                                                          │
│  ┌──────────────────┐                                   │
│  │ Mock Service     │                                   │
│  │ (Node.js)        │                                   │
│  │                  │                                   │
│  │ Simula:          │                                   │
│  │ - WhatsApp API   │                                   │
│  │ - Telegram API   │                                   │
│  └────────┬─────────┘                                   │
│           │                                              │
│           │ HTTP POST (webhook)                         │
│           │                                              │
│           ▼                                              │
│  ┌──────────────────┐       ┌──────────────────┐       │
│  │ Cloudflare       │──────>│ Seu Servidor     │       │
│  │ Tunnel           │       │ localhost:8081   │       │
│  │ (Opcional)       │       │                  │       │
│  │                  │       │ - Recebe webhook │       │
│  │ Expõe:           │       │ - Processa       │       │
│  │ https://abc.     │       │ - Salva MongoDB  │       │
│  │ trycloudflare    │       │ - Publica Kafka  │       │
│  │ .com             │       │                  │       │
│  └──────────────────┘       └──────────────────┘       │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

**Vantagens:**
- ✅ Testa webhooks sem WhatsApp/Telegram reais
- ✅ Controle total dos payloads
- ✅ Simula diferentes cenários (mensagens, status, erros)
- ✅ Valida assinaturas HMAC
- ✅ Testa com/sem Cloudflare Tunnel

---

## 🔧 Pré-requisitos

### 1. Node.js Instalado
```powershell
node --version
# Deve mostrar v16+ ou superior
```

### 2. Servidor Chat Rodando
```powershell
# Inicie infraestrutura + servidor
.\restart.ps1
```

### 3. (Opcional) Cloudflare Tunnel Instalado
```powershell
cloudflared --version
# Se não instalado: winget install --id Cloudflare.cloudflared
```

---

## 🧪 Cenário 1: Teste Local (sem Cloudflare)

**Quando usar:** Desenvolvimento rápido, testes unitários, CI/CD

### Passo 1: Inicie o servidor
```powershell
# Terminal 1
.\restart.ps1
```

### Passo 2: Envie webhook mock do WhatsApp
```powershell
# Terminal 2
node scripts\setup\mock-webhook-sender.js `
  --url http://localhost:8081 `
  --platform whatsapp `
  --type textMessage
```

**Saída esperada:**
```
🚀 Mock Webhook Sender Iniciado
Target URL: http://localhost:8081
Platform: whatsapp
Payload Type: textMessage

================================================================================
📤 Enviando webhook WHATSAPP - textMessage
================================================================================
URL: http://localhost:8081/api/webhooks/whatsapp
Headers:
  Content-Type: application/json
  X-Hub-Signature-256: sha256=abc123...

Payload:
{
  "object": "whatsapp_business_account",
  "entry": [...]
}

✅ Resposta recebida - Status: 200
================================================================================
```

### Passo 3: Envie webhook mock do Telegram
```powershell
node scripts\setup\mock-webhook-sender.js `
  --url http://localhost:8081 `
  --platform telegram `
  --type textMessage
```

### Passo 4: Teste ambas as plataformas
```powershell
node scripts\setup\mock-webhook-sender.js `
  --url http://localhost:8081 `
  --platform both
```

### Passo 5: Verifique os logs do servidor
```
2025-12-04 23:15:42 [http-nio-8081-exec-1] INFO  c.c.w.WebhookController
  - [WEBHOOK] WhatsApp webhook recebido - signature válida
2025-12-04 23:15:42 [http-nio-8081-exec-1] INFO  c.c.w.WhatsappWebhookWorker
  - Processing WhatsApp message event - from: 5511999887766
```

---

## 🌐 Cenário 2: Teste com Cloudflare Tunnel

**Quando usar:** Simular ambiente de produção, testar NAT traversal, validar URLs públicas

### Passo 1: Inicie o servidor
```powershell
# Terminal 1
.\restart.ps1
```

### Passo 2: Inicie Cloudflare Tunnel
```powershell
# Terminal 2
cloudflared tunnel --url http://localhost:8081
```

**Saída:**
```
2025-12-04 23:20:15 INF +--------------------------------------------------------------------------------------------+
2025-12-04 23:20:15 INF |  Your quick Tunnel has been created! Visit it at (it may take some time to be reachable):  |
2025-12-04 23:20:15 INF |  https://random-name-1234.trycloudflare.com                                                |
2025-12-04 23:20:15 INF +--------------------------------------------------------------------------------------------+
```

**⚠️ COPIE a URL:** `https://random-name-1234.trycloudflare.com`

### Passo 3: Teste com a URL pública
```powershell
# Terminal 3 - Substitua pela URL do seu tunnel
node scripts\setup\mock-webhook-sender.js `
  --url https://random-name-1234.trycloudflare.com `
  --platform whatsapp
```

### Passo 4: Valide o fluxo completo
O webhook vai seguir este caminho:
```
Mock Script → HTTPS → Cloudflare Cloud → Tunnel → localhost:8081 → Webhook Handler
```

---

## 🔄 Cenário 3: Mock Contínuo (Simulador de Tráfego)

Crie um script que envia webhooks periodicamente para simular tráfego real:

```powershell
# Criar script de teste contínuo
New-Item -Path "scripts\setup\continuous-webhook-test.ps1" -ItemType File
```

**Conteúdo do `continuous-webhook-test.ps1`:**
```powershell
# Teste Contínuo de Webhooks
param(
    [string]$Url = "http://localhost:8081",
    [int]$IntervalSeconds = 10,
    [int]$Count = 100
)

Write-Host "🔄 Iniciando teste contínuo de webhooks" -ForegroundColor Cyan
Write-Host "URL: $Url"
Write-Host "Intervalo: ${IntervalSeconds}s"
Write-Host "Total: $Count iterações"
Write-Host ""

$platforms = @("whatsapp", "telegram")
$types = @("textMessage", "messageDelivered", "messageRead")

for ($i = 1; $i -le $Count; $i++) {
    $platform = $platforms[$i % 2]
    $type = $types[$i % 3]
    
    Write-Host "[$i/$Count] Enviando $platform - $type..." -ForegroundColor Yellow
    
    node scripts\setup\mock-webhook-sender.js `
        --url $Url `
        --platform $platform `
        --type $type
    
    if ($i -lt $Count) {
        Start-Sleep -Seconds $IntervalSeconds
    }
}

Write-Host ""
Write-Host "✅ Teste contínuo finalizado!" -ForegroundColor Green
```

**Executar:**
```powershell
# Teste local - 20 webhooks a cada 5 segundos
.\scripts\setup\continuous-webhook-test.ps1 `
  -Url "http://localhost:8081" `
  -IntervalSeconds 5 `
  -Count 20

# Teste com Cloudflare Tunnel
.\scripts\setup\continuous-webhook-test.ps1 `
  -Url "https://random-name-1234.trycloudflare.com" `
  -IntervalSeconds 10 `
  -Count 50
```

---

## 🔍 Validação e Troubleshooting

### 1. Verificar Webhooks Recebidos

**MongoDB:**
```javascript
// Conectar ao MongoDB
mongosh mongodb://localhost:27017/chat-db-dev

// Ver mensagens recebidas
db.messages.find().sort({createdAt: -1}).limit(10).pretty()

// Contar mensagens por plataforma
db.messages.aggregate([
  { $group: { _id: "$platform", count: { $sum: 1 } } }
])
```

**Logs do Servidor:**
```powershell
# Filtrar apenas logs de webhook
Get-Content logs\application.log | Select-String "WEBHOOK"
```

### 2. Validar Assinaturas

**WhatsApp (HMAC SHA256):**
```powershell
# O servidor deve logar:
# [WEBHOOK] WhatsApp webhook recebido - signature válida ✅

# Se inválido:
# [WEBHOOK] WhatsApp webhook rejeitado - signature inválida ❌
```

**Telegram (Secret Token):**
```powershell
# Verifique application.yml:
telegram:
  webhook:
    secret-token: telegram-test-secret-key-12345

# Deve coincidir com mock-webhook-sender.js
```

### 3. Testar Diferentes Tipos de Payload

**WhatsApp:**
```powershell
# Mensagem de texto
node scripts\setup\mock-webhook-sender.js --url http://localhost:8081 --platform whatsapp --type textMessage

# Status entregue
node scripts\setup\mock-webhook-sender.js --url http://localhost:8081 --platform whatsapp --type messageDelivered

# Status lido
node scripts\setup\mock-webhook-sender.js --url http://localhost:8081 --platform whatsapp --type messageRead
```

**Telegram:**
```powershell
# Mensagem de texto
node scripts\setup\mock-webhook-sender.js --url http://localhost:8081 --platform telegram --type textMessage

# Foto
node scripts\setup\mock-webhook-sender.js --url http://localhost:8081 --platform telegram --type photoMessage

# Callback query (botão)
node scripts\setup\mock-webhook-sender.js --url http://localhost:8081 --platform telegram --type callbackQuery
```

### 4. Problemas Comuns

#### ❌ Erro: `ECONNREFUSED`
```
Causa: Servidor não está rodando
Solução: .\restart.ps1
```

#### ❌ Erro: `signature inválida`
```
Causa: Secret key não coincide
Solução: Verificar application.yml vs mock-webhook-sender.js
```

#### ❌ Cloudflare Tunnel não conecta
```
Causa: Firewall bloqueando OUTBOUND
Solução: Permitir cloudflared.exe no firewall
```

#### ❌ Webhook retorna 404
```
Causa: Endpoint incorreto
Solução: Verificar /api/webhooks/whatsapp ou /api/webhooks/telegram
```

---

## 📊 Métricas e Monitoramento

### Verificar Performance
```powershell
# Enviar 100 webhooks e medir tempo
Measure-Command {
    for ($i = 1; $i -le 100; $i++) {
        node scripts\setup\mock-webhook-sender.js `
            --url http://localhost:8081 `
            --platform both
    }
}
```

### Monitorar Kafka
```powershell
# Abrir Kafka UI
Start-Process "http://localhost:8090"

# Ver tópicos:
# - message-events
# - state-update-events
# - whatsapp-messages
# - telegram-messages
```

### Verificar MongoDB
```javascript
// Estatísticas
db.messages.stats()

// Mensagens nas últimas 24h
db.messages.find({
  createdAt: { $gte: new Date(Date.now() - 24*60*60*1000) }
}).count()
```

---

## 🎯 Casos de Uso

### Caso 1: Testar Rate Limiting
```powershell
# Enviar 1000 webhooks rapidamente
.\scripts\setup\continuous-webhook-test.ps1 `
  -Url "http://localhost:8081" `
  -IntervalSeconds 0 `
  -Count 1000
```

### Caso 2: Testar Diferentes Remetentes
Edite `mock-webhook-sender.js` e altere:
```javascript
// WhatsApp - Múltiplos remetentes
messages: [{
  from: '5511' + Math.floor(Math.random() * 1000000000), // Número aleatório
  // ...
}]

// Telegram - Múltiplos usuários
from: {
  id: Math.floor(Math.random() * 1000000000), // User ID aleatório
  // ...
}
```

### Caso 3: Testar Mensagens Grandes
```javascript
// Mensagem com 4096 caracteres (limite Telegram)
text: 'A'.repeat(4096)
```

---

## ✅ Checklist de Validação

- [ ] Mock envia webhook para localhost:8081
- [ ] Mock envia webhook através de Cloudflare Tunnel
- [ ] Assinatura HMAC validada corretamente (WhatsApp)
- [ ] Secret token validado corretamente (Telegram)
- [ ] Mensagem salva no MongoDB
- [ ] Evento publicado no Kafka
- [ ] WebSocket notifica clientes conectados
- [ ] Logs mostram processamento correto
- [ ] Performance aceitável (< 100ms por webhook)
- [ ] Testes contínuos executam sem erros

---

## 🚀 Próximos Passos

1. **Testes Automatizados:** Integrar mocks em testes unitários/integração
2. **CI/CD:** Executar continuous-webhook-test.ps1 no pipeline
3. **Produção:** Substituir mocks por webhooks reais WhatsApp/Telegram
4. **Monitoramento:** Adicionar alertas para falhas de webhook

---

## 📚 Referências

- [Cloudflare Tunnel Setup](GUIA-CLOUDFLARE-TUNNEL-SETUP.md)
- [WhatsApp Webhook Reference](https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks)
- [Telegram Webhook Guide](https://core.telegram.org/bots/webhooks)
- [Mock Service Code](../scripts/setup/mock-webhook-sender.js)
