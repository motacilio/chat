# Scripts de Setup e Teste

Este diretório contém scripts utilitários para configuração e teste do sistema de chat.

## 📁 Arquivos

### Webhooks e Testes
- **`mock-webhook-sender.js`** - Envia webhooks simulados do WhatsApp/Telegram
- **`continuous-webhook-test.ps1`** - Teste de carga com múltiplos webhooks
- **`test-websocket-client.js`** - Cliente WebSocket para testes de notificações

### Dados de Teste
- **`seed-test-data.ps1`** - Popula MongoDB com dados de exemplo
- **`seed-recipient-contacts.ps1`** - Cria contatos para testes

### Monitoramento
- **`test-streaming.ps1`** - Testa streaming de mensagens

---

## 🚀 Quick Start - Teste de Webhooks com Mocks

### Pré-requisitos
```powershell
# Verificar Node.js instalado
node --version

# Verificar servidor rodando
Test-NetConnection localhost -Port 8081
```

### 1. Teste Simples (1 webhook)

**WhatsApp:**
```powershell
node scripts\setup\mock-webhook-sender.js `
  --url http://localhost:8081 `
  --platform whatsapp `
  --type textMessage
```

**Telegram:**
```powershell
node scripts\setup\mock-webhook-sender.js `
  --url http://localhost:8081 `
  --platform telegram `
  --type textMessage
```

**Ambas as plataformas:**
```powershell
node scripts\setup\mock-webhook-sender.js `
  --url http://localhost:8081 `
  --platform both
```

### 2. Teste com Cloudflare Tunnel

**Terminal 1 - Inicie o tunnel:**
```powershell
cloudflared tunnel --url http://localhost:8081
# Copie a URL: https://random-name-1234.trycloudflare.com
```

**Terminal 2 - Envie webhook:**
```powershell
node scripts\setup\mock-webhook-sender.js `
  --url https://random-name-1234.trycloudflare.com `
  --platform whatsapp
```

### 3. Teste de Carga (100 webhooks)

```powershell
.\scripts\setup\continuous-webhook-test.ps1 `
  -Url "http://localhost:8081" `
  -Count 100 `
  -IntervalSeconds 2
```

**Com Cloudflare Tunnel:**
```powershell
.\scripts\setup\continuous-webhook-test.ps1 `
  -Url "https://random-name-1234.trycloudflare.com" `
  -Count 50 `
  -IntervalSeconds 5
```

**Modo aleatório:**
```powershell
.\scripts\setup\continuous-webhook-test.ps1 `
  -Url "http://localhost:8081" `
  -Random `
  -Count 50
```

---

## 🔧 Configuração

### Secret Keys - IMPORTANTE!

Os secret keys devem coincidir entre:
- **application-dev.yml**
- **mock-webhook-sender.js**

**application-dev.yml:**
```yaml
webhook:
  whatsapp:
    secret-key: whatsapp-test-secret-key-12345
  telegram:
    secret-token: telegram-test-secret-key-12345
```

**mock-webhook-sender.js:**
```javascript
const config = {
  whatsapp: {
    secret: 'whatsapp-test-secret-key-12345',
  },
  telegram: {
    secret: 'telegram-test-secret-key-12345',
  }
};
```

⚠️ **Se os secrets não coincidirem, a validação de assinatura falhará!**

---

## 📊 Validação

### Verificar logs do servidor
```powershell
# Filtrar apenas webhooks
Get-Content logs\application.log | Select-String "WEBHOOK"
```

**Saída esperada:**
```
[WEBHOOK] WhatsApp webhook recebido - signature válida ✅
[WEBHOOK] Processing WhatsApp message event - from: 5511999887766
```

### Verificar MongoDB
```javascript
// Conectar
mongosh mongodb://localhost:27017/chat

// Ver mensagens
db.messages.find().sort({createdAt: -1}).limit(10).pretty()

// Contar por plataforma
db.messages.aggregate([
  { $group: { _id: "$platform", count: { $sum: 1 } } }
])
```

### Verificar Kafka
```powershell
# Abrir Kafka UI
Start-Process "http://localhost:8090"

# Ver tópicos: message-events, whatsapp-messages, telegram-messages
```

---

## 🎯 Tipos de Payload Disponíveis

### WhatsApp
- **`textMessage`** - Mensagem de texto recebida
- **`messageDelivered`** - Status de mensagem entregue
- **`messageRead`** - Status de mensagem lida

**Exemplo:**
```powershell
node scripts\setup\mock-webhook-sender.js `
  --url http://localhost:8081 `
  --platform whatsapp `
  --type messageDelivered
```

### Telegram
- **`textMessage`** - Mensagem de texto recebida
- **`photoMessage`** - Mensagem com foto
- **`callbackQuery`** - Botão pressionado

**Exemplo:**
```powershell
node scripts\setup\mock-webhook-sender.js `
  --url http://localhost:8081 `
  --platform telegram `
  --type photoMessage
```

---

## 🐛 Troubleshooting

### ❌ Erro: `ECONNREFUSED`
```
Causa: Servidor não está rodando
Solução: .\restart.ps1
```

### ❌ Erro: `signature inválida`
```
Causa: Secret key não coincide
Solução: Verificar application-dev.yml vs mock-webhook-sender.js
```

### ❌ Webhook retorna 404
```
Causa: Endpoint incorreto
Solução: Verificar URL - deve ter /api/webhooks/whatsapp ou /api/webhooks/telegram
```

### ❌ Cloudflare Tunnel não conecta
```
Causa: Firewall bloqueando OUTBOUND
Solução: Permitir cloudflared.exe no firewall do Windows
```

### ❌ Node.js não encontrado
```
Causa: Node.js não instalado
Solução: Baixar em https://nodejs.org
```

---

## 📚 Documentação Completa

Para guias detalhados, consulte:
- **[GUIA-TESTE-WEBHOOKS-COM-MOCKS.md](../../docs/GUIA-TESTE-WEBHOOKS-COM-MOCKS.md)** - Guia completo de testes
- **[GUIA-CLOUDFLARE-TUNNEL-SETUP.md](../../docs/GUIA-CLOUDFLARE-TUNNEL-SETUP.md)** - Setup do Cloudflare Tunnel
- **[GUIA-TESTES-MOCKS.md](../../docs/GUIA-TESTES-MOCKS.md)** - Testes com mocks gerais

---

## 💡 Dicas

1. **Sempre inicie o servidor antes de enviar webhooks**
2. **Use Cloudflare Tunnel para simular ambiente de produção**
3. **Verifique os logs do servidor para debugging**
4. **Use modo aleatório no teste contínuo para simular tráfego real**
5. **Monitore MongoDB e Kafka durante testes de carga**

---

## 🎓 Exemplos de Uso

### Desenvolvimento Local
```powershell
# Terminal 1: Servidor
.\restart.ps1

# Terminal 2: Enviar webhook de teste
node scripts\setup\mock-webhook-sender.js --url http://localhost:8081 --platform both
```

### Teste de Integração
```powershell
# Terminal 1: Servidor
.\restart.ps1

# Terminal 2: Cloudflare Tunnel
cloudflared tunnel --url http://localhost:8081

# Terminal 3: Teste contínuo via tunnel
.\scripts\setup\continuous-webhook-test.ps1 `
  -Url "https://abc-123.trycloudflare.com" `
  -Count 20
```

### CI/CD Pipeline
```powershell
# Teste automatizado (sem delay)
.\scripts\setup\continuous-webhook-test.ps1 `
  -Url "http://localhost:8081" `
  -Count 10 `
  -IntervalSeconds 0
```
