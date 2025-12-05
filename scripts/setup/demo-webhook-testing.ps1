# Demo Completo - Teste de Webhooks com Mocks
# Este script demonstra todos os recursos do sistema de testes

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   DEMO COMPLETO - WEBHOOK MOCK TESTING" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

# Verificar pre-requisitos
Write-Host "1. Verificando pre-requisitos..." -ForegroundColor Yellow
Write-Host ""

# Node.js
$nodeVersion = node --version 2>$null
if ($nodeVersion) {
    Write-Host "  OK Node.js: $nodeVersion" -ForegroundColor Green
} else {
    Write-Host "  ERRO Node.js nao instalado" -ForegroundColor Red
    Write-Host "    Instale em: https://nodejs.org" -ForegroundColor Gray
    exit 1
}

# Servidor
$serverRunning = Test-NetConnection -ComputerName localhost -Port 8081 -InformationLevel Quiet -WarningAction SilentlyContinue
if ($serverRunning) {
    Write-Host "  OK Servidor rodando na porta 8081" -ForegroundColor Green
} else {
    Write-Host "  ERRO Servidor nao esta rodando" -ForegroundColor Red
    Write-Host "    Execute: .\restart.ps1" -ForegroundColor Gray
    exit 1
}

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

# Demo 1: Webhook unico WhatsApp
Write-Host "2. Enviando webhook WhatsApp (mensagem de texto)..." -ForegroundColor Yellow
Write-Host ""
node scripts\setup\mock-webhook-sender.js `
    --url http://localhost:8081 `
    --platform whatsapp `
    --type textMessage

Start-Sleep -Seconds 2

# Demo 2: Webhook unico Telegram
Write-Host ""
Write-Host "3. Enviando webhook Telegram (mensagem de texto)..." -ForegroundColor Yellow
Write-Host ""
node scripts\setup\mock-webhook-sender.js `
    --url http://localhost:8081 `
    --platform telegram `
    --type textMessage

Start-Sleep -Seconds 2

# Demo 3: Status updates WhatsApp
Write-Host ""
Write-Host "4. Enviando status updates WhatsApp..." -ForegroundColor Yellow
Write-Host ""

Write-Host "  -> Status: DELIVERED" -ForegroundColor Gray
node scripts\setup\mock-webhook-sender.js `
    --url http://localhost:8081 `
    --platform whatsapp `
    --type messageDelivered

Start-Sleep -Seconds 1

Write-Host "  -> Status: READ" -ForegroundColor Gray
node scripts\setup\mock-webhook-sender.js `
    --url http://localhost:8081 `
    --platform whatsapp `
    --type messageRead

Start-Sleep -Seconds 2

# Demo 4: Diferentes tipos Telegram
Write-Host ""
Write-Host "5. Testando diferentes tipos de mensagem Telegram..." -ForegroundColor Yellow
Write-Host ""

Write-Host "  -> Foto" -ForegroundColor Gray
node scripts\setup\mock-webhook-sender.js `
    --url http://localhost:8081 `
    --platform telegram `
    --type photoMessage

Start-Sleep -Seconds 1

Write-Host "  -> Callback Query (botao)" -ForegroundColor Gray
node scripts\setup\mock-webhook-sender.js `
    --url http://localhost:8081 `
    --platform telegram `
    --type callbackQuery

Start-Sleep -Seconds 2

# Demo 5: Teste de carga leve
Write-Host ""
Write-Host "6. Executando teste de carga leve (10 webhooks)..." -ForegroundColor Yellow
Write-Host ""

.\scripts\setup\continuous-webhook-test.ps1 `
    -Url "http://localhost:8081" `
    -Count 10 `
    -IntervalSeconds 1 `
    -Random

# Finalizacao
Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   DEMO FINALIZADO COM SUCESSO!" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Proximos passos:" -ForegroundColor White
Write-Host ""
Write-Host "  1. Verificar MongoDB:" -ForegroundColor Gray
Write-Host "     mongosh mongodb://localhost:27017/chat" -ForegroundColor Gray
Write-Host '     db.messages.find().sort({createdAt: -1}).limit(10)' -ForegroundColor Gray
Write-Host ""
Write-Host "  2. Verificar Kafka UI:" -ForegroundColor Gray
Write-Host "     Start-Process 'http://localhost:8090'" -ForegroundColor Gray
Write-Host ""
Write-Host "  3. Verificar logs do servidor:" -ForegroundColor Gray
Write-Host "     Get-Content logs\application.log | Select-String 'WEBHOOK'" -ForegroundColor Gray
Write-Host ""
Write-Host "  4. Testar com Cloudflare Tunnel:" -ForegroundColor Gray
Write-Host "     cloudflared tunnel --url http://localhost:8081" -ForegroundColor Gray
Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""
