# Script de Teste: Streaming de Mensagens em Tempo Real
# Demonstra o funcionamento do StreamMessages endpoint com validação de segurança

# Configuração
$baseUrl = "http://localhost:8081"
$grpcUrl = "localhost:9090"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  TESTE: Streaming de Mensagens em Tempo Real" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

# Função para obter token JWT
function Get-JwtToken {
    param($username, $password)
    
    $body = @{
        username = $username
        password = $password
    } | ConvertTo-Json
    
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $body -ContentType "application/json"
        return $response.token
    } catch {
        Write-Host "❌ Erro ao obter token para $username : $_" -ForegroundColor Red
        return $null
    }
}

# Passo 1: Obter tokens para Alice, Bob e Charlie
Write-Host "📝 Passo 1: Autenticando usuários..." -ForegroundColor Yellow
Write-Host ""

$tokenAlice = Get-JwtToken -username "alice" -password "password123"
$tokenBob = Get-JwtToken -username "bob" -password "password123"
$tokenCharlie = Get-JwtToken -username "charlie" -password "password123"

if (-not $tokenAlice -or -not $tokenBob -or -not $tokenCharlie) {
    Write-Host "❌ Falha na autenticação. Verifique se a aplicação está rodando." -ForegroundColor Red
    exit 1
}

Write-Host "✅ Alice autenticada" -ForegroundColor Green
Write-Host "✅ Bob autenticado" -ForegroundColor Green
Write-Host "✅ Charlie autenticado" -ForegroundColor Green
Write-Host ""

# IDs dos usuários
$aliceId = "a1a1a1a1-1111-1111-1111-111111111111"
$bobId = "b2b2b2b2-2222-2222-2222-222222222222"
$charlieId = "c3c3c3c3-3333-3333-3333-333333333333"

# Passo 2: Verificar se grpcurl está instalado
Write-Host "📝 Passo 2: Verificando grpcurl..." -ForegroundColor Yellow
Write-Host ""

$grpcurlExists = Get-Command grpcurl -ErrorAction SilentlyContinue
if (-not $grpcurlExists) {
    Write-Host "❌ grpcurl não encontrado!" -ForegroundColor Red
    Write-Host ""
    Write-Host "📥 Para instalar grpcurl:" -ForegroundColor Cyan
    Write-Host "  1. Via Chocolatey: choco install grpcurl" -ForegroundColor White
    Write-Host "  2. Via Scoop: scoop install grpcurl" -ForegroundColor White
    Write-Host "  3. Download manual: https://github.com/fullstorydev/grpcurl/releases" -ForegroundColor White
    Write-Host ""
    Write-Host "⚠️  Pulando testes gRPC. Use o guia manual em docs/GUIA-STREAMING-TEMPO-REAL.md" -ForegroundColor Yellow
    exit 0
}

Write-Host "✅ grpcurl encontrado" -ForegroundColor Green
Write-Host ""

# Passo 3: Teste de Segurança - user_id spoofing
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  TESTE DE SEGURANÇA: Prevenir user_id Spoofing" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "🔐 Cenário: Charlie tenta abrir stream de Alice usando token do Charlie" -ForegroundColor Yellow
Write-Host "   Esperado: PERMISSION_DENIED" -ForegroundColor Yellow
Write-Host ""

# Criar arquivo temporário com request
$spoofRequest = @"
{
  "user_id": "$aliceId"
}
"@

$spoofFile = [System.IO.Path]::GetTempFileName()
Set-Content -Path $spoofFile -Value $spoofRequest

# Executar grpcurl (espera falhar com PERMISSION_DENIED)
Write-Host "📤 Executando: grpcurl com token do Charlie mas user_id da Alice..." -ForegroundColor White

$result = & grpcurl -plaintext `
    -H "authorization: Bearer $tokenCharlie" `
    -d "@$spoofFile" `
    $grpcUrl chat_api.v1.ChatService/StreamMessages 2>&1

# Verificar se retornou PERMISSION_DENIED
if ($result -match "PERMISSION_DENIED" -or $result -match "user_id must match authenticated user") {
    Write-Host "✅ SUCESSO: Validação de segurança bloqueou spoofing!" -ForegroundColor Green
    Write-Host "   Mensagem: user_id must match authenticated user" -ForegroundColor Gray
} else {
    Write-Host "❌ FALHA: Validação de segurança não funcionou!" -ForegroundColor Red
    Write-Host "   Resposta: $result" -ForegroundColor Gray
}

Remove-Item $spoofFile -Force
Write-Host ""

# Passo 4: Informações sobre teste manual de streaming
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  PRÓXIMOS PASSOS: Teste Manual de Streaming" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "⚠️  NOTA: O streaming é uma conexão de longa duração (server-side streaming)." -ForegroundColor Yellow
Write-Host "   Não é possível automatizar completamente em script PowerShell." -ForegroundColor Yellow
Write-Host ""
Write-Host "📖 Para testar o streaming completo, siga o guia:" -ForegroundColor Cyan
Write-Host "   docs/GUIA-STREAMING-TEMPO-REAL.md" -ForegroundColor White
Write-Host ""
Write-Host "🔧 Comandos úteis para teste manual:" -ForegroundColor Cyan
Write-Host ""

# Comando 1: Abrir stream de Alice
Write-Host "1️⃣  Abrir stream de Alice (Terminal 1):" -ForegroundColor Yellow
Write-Host "   grpcurl -plaintext ```" -ForegroundColor White
Write-Host "     -H `"authorization: Bearer $tokenAlice`" ```" -ForegroundColor White
Write-Host "     -d '{`"user_id`": `"$aliceId`"}' ```" -ForegroundColor White
Write-Host "     $grpcUrl chat_api.v1.ChatService/StreamMessages" -ForegroundColor White
Write-Host ""

# Comando 2: Bob cria conversa
Write-Host "2️⃣  Bob cria conversa com Alice (Terminal 2):" -ForegroundColor Yellow
Write-Host "   grpcurl -plaintext ```" -ForegroundColor White
Write-Host "     -H `"authorization: Bearer $tokenBob`" ```" -ForegroundColor White
Write-Host "     -d '{" -ForegroundColor White
Write-Host "       `"creator_id`": `"$bobId`"," -ForegroundColor White
Write-Host "       `"type`": `"PRIVATE`"," -ForegroundColor White
Write-Host "       `"participant_ids`": [`"$bobId`", `"$aliceId`"]" -ForegroundColor White
Write-Host "     }' ```" -ForegroundColor White
Write-Host "     $grpcUrl chat_api.v1.ConversationService/CreateConversation" -ForegroundColor White
Write-Host ""

# Comando 3: Bob envia mensagem
Write-Host "3️⃣  Bob envia mensagem (Terminal 2):" -ForegroundColor Yellow
Write-Host "   # Substitua {CONVERSATION_ID} pelo ID retornado no passo 2" -ForegroundColor Gray
Write-Host "   grpcurl -plaintext ```" -ForegroundColor White
Write-Host "     -H `"authorization: Bearer $tokenBob`" ```" -ForegroundColor White
Write-Host "     -d '{" -ForegroundColor White
Write-Host "       `"conversation_id`": `"{CONVERSATION_ID}`"," -ForegroundColor White
Write-Host "       `"sender_id`": `"$bobId`"," -ForegroundColor White
Write-Host "       `"message_text`": `"Olá Alice, testando streaming!`"" -ForegroundColor White
Write-Host "     }' ```" -ForegroundColor White
Write-Host "     $grpcUrl chat_api.v1.ChatService/SendMessage" -ForegroundColor White
Write-Host ""

Write-Host "✨ Alice (Terminal 1) deve receber a mensagem em tempo real!" -ForegroundColor Green
Write-Host ""

# Resumo de tokens para copy-paste
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  TOKENS JWT (válidos por 24h)" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Alice:" -ForegroundColor Yellow
Write-Host $tokenAlice -ForegroundColor White
Write-Host ""
Write-Host "Bob:" -ForegroundColor Yellow
Write-Host $tokenBob -ForegroundColor White
Write-Host ""
Write-Host "Charlie:" -ForegroundColor Yellow
Write-Host $tokenCharlie -ForegroundColor White
Write-Host ""

# Salvar tokens em arquivo temporário
$tokensFile = "tokens-temp.txt"
$tokensContent = @"
# Tokens JWT - Gerados em $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
# Válidos por 24 horas

ALICE_TOKEN=$tokenAlice
BOB_TOKEN=$tokenBob
CHARLIE_TOKEN=$tokenCharlie

ALICE_ID=$aliceId
BOB_ID=$bobId
CHARLIE_ID=$charlieId

GRPC_URL=$grpcUrl
"@

Set-Content -Path $tokensFile -Value $tokensContent
Write-Host "💾 Tokens salvos em: $tokensFile" -ForegroundColor Cyan
Write-Host ""

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  ✅ Validação de Segurança: CONCLUÍDA" -ForegroundColor Green
Write-Host "  📖 Guia completo: docs/GUIA-STREAMING-TEMPO-REAL.md" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
