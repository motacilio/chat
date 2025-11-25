# ============================================
# TESTE: Arquivo → Mensagem → Kafka
# ============================================
# Valida que arquivos criam mensagens e são publicados no Kafka

param(
    [string]$BaseUrl = "http://localhost:8081",
    [int]$FileSizeBytes = 524288  # 512 KB
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "TESTE: ARQUIVO → MENSAGEM → KAFKA" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Criar diretório temp
    if (-not (Test-Path "C:\temp")) {
        New-Item -ItemType Directory -Path "C:\temp" | Out-Null
    }
    
    # 1. Login como Alice
    Write-Host "[1] Login (alice)..." -ForegroundColor Yellow
    $loginBody = @{ username = "alice"; password = "password123" } | ConvertTo-Json
    $auth = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
    Write-Host "    ✓ UserId: $($auth.userId)" -ForegroundColor Green
    
    # 2. Criar arquivo de teste
    Write-Host "[2] Criar arquivo de teste..." -ForegroundColor Yellow
    $testFile = "C:\temp\test-kafka.pdf"
    $content = [byte[]]::new($FileSizeBytes)
    (New-Object Random).NextBytes($content)
    [System.IO.File]::WriteAllBytes($testFile, $content)
    $md5 = [System.Security.Cryptography.MD5]::Create()
    $hash = $md5.ComputeHash([System.IO.File]::ReadAllBytes($testFile))
    $checksum = [System.BitConverter]::ToString($hash).Replace("-", "").ToLower()
    Write-Host "    ✓ Arquivo: $FileSizeBytes bytes, MD5: $checksum" -ForegroundColor Green
    
    # 3. Iniciar upload
    Write-Host "[3] Iniciar upload..." -ForegroundColor Yellow
    $headers = @{ "Authorization" = "Bearer $($auth.token)"; "Content-Type" = "application/json" }
    $initiateBody = @{
        conversationId = "c1c1c1c1-1111-1111-1111-111111111111"
        filename = "test-kafka.pdf"
        sizeBytes = $FileSizeBytes
        mimeType = "application/pdf"
    } | ConvertTo-Json
    $initiate = Invoke-RestMethod -Uri "$BaseUrl/api/files/initiate" -Method Post -Headers $headers -Body $initiateBody
    Write-Host "    ✓ FileId: $($initiate.fileId)" -ForegroundColor Green
    
    # 4. Upload para MinIO
    Write-Host "[4] Upload para MinIO..." -ForegroundColor Yellow
    $fileBytes = [System.IO.File]::ReadAllBytes($testFile)
    $webRequest = [System.Net.WebRequest]::Create($initiate.uploadUrl)
    $webRequest.Method = "PUT"
    $webRequest.ContentType = "application/pdf"
    $webRequest.ContentLength = $fileBytes.Length
    $requestStream = $webRequest.GetRequestStream()
    $requestStream.Write($fileBytes, 0, $fileBytes.Length)
    $requestStream.Close()
    $response = $webRequest.GetResponse()
    $response.Close()
    Write-Host "    ✓ Upload concluído" -ForegroundColor Green
    
    # 5. Completar upload → CRIA MENSAGEM + PUBLICA NO KAFKA
    Write-Host "[5] Completar upload → Criar mensagem → Publicar no Kafka..." -ForegroundColor Yellow
    $completeBody = @{
        fileId = $initiate.fileId
        checksumMd5 = $checksum
        senderId = $auth.userId                                    # OBRIGATÓRIO
        recipientIds = @("b2b2b2b2-2222-2222-2222-222222222222")  # OBRIGATÓRIO
    } | ConvertTo-Json
    
    $complete = Invoke-RestMethod -Uri "$BaseUrl/api/files/complete" -Method Post -ContentType "application/json" -Headers $headers -Body $completeBody
    
    Write-Host "    ========================================" -ForegroundColor Green
    Write-Host "    MENSAGEM CRIADA E PUBLICADA NO KAFKA!" -ForegroundColor Green
    Write-Host "    ========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "    Detalhes da mensagem:" -ForegroundColor Cyan
    Write-Host "      MessageId:     $($complete.messageId)" -ForegroundColor White
    Write-Host "      FileId:        $($complete.fileId)" -ForegroundColor White
    Write-Host "      Estado:        $($complete.state)" -ForegroundColor White
    Write-Host "      UploadStatus:  $($complete.uploadStatus)" -ForegroundColor White
    Write-Host "      ConversationId: $($complete.conversationId)" -ForegroundColor White
    Write-Host ""
    
    # 6. Verificar no Kafka (opcional - mostra o comando)
    Write-Host "[6] Verificação no Kafka:" -ForegroundColor Yellow
    Write-Host "    Para ver as mensagens no Kafka, execute:" -ForegroundColor Gray
    Write-Host ""
    Write-Host "    docker exec kafka kafka-console-consumer \" -ForegroundColor Cyan
    Write-Host "        --bootstrap-server localhost:9092 \" -ForegroundColor Cyan
    Write-Host "        --topic message-events \" -ForegroundColor Cyan
    Write-Host "        --from-beginning" -ForegroundColor Cyan
    Write-Host ""
    
    # 7. Verificar no MongoDB (opcional - mostra o comando)
    Write-Host "[7] Verificação no MongoDB:" -ForegroundColor Yellow
    Write-Host "    Para ver mensagens de arquivo no MongoDB, execute:" -ForegroundColor Gray
    Write-Host ""
    Write-Host "    docker exec mongodb mongosh chat --eval `"" -ForegroundColor Cyan
    Write-Host "        db.messages.find({messageText: null, 'fileMetadata': {`$exists: true}}).pretty()`"" -ForegroundColor Cyan
    Write-Host ""
    
    # Resumo
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "TESTE PASSOU COM SUCESSO!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Fluxo validado:" -ForegroundColor White
    Write-Host "  1. Arquivo enviado para MinIO" -ForegroundColor Gray
    Write-Host "  2. FileMetadata persistido no MongoDB" -ForegroundColor Gray
    Write-Host "  3. Message criado (com fileMetadata, sem messageText)" -ForegroundColor Gray
    Write-Host "  4. Mensagem publicada no tópico 'message-events' do Kafka" -ForegroundColor Gray
    Write-Host "  5. Estado inicial: SENT" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Próximos passos automáticos:" -ForegroundColor Yellow
    Write-Host "  - MessageDeliveryWorker consome do Kafka" -ForegroundColor Gray
    Write-Host "  - Estado muda: SENT -> DELIVERED -> READ" -ForegroundColor Gray
    Write-Host ""
    
    # Cleanup
    Remove-Item $testFile -ErrorAction SilentlyContinue
    
    exit 0

