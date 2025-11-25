# ============================================
# SCRIPT DE TESTE AUTOMATIZADO - Layer 2 File Upload
# ============================================
# Valida fluxo completo de upload/download de arquivos
# Requer: Spring Boot rodando, MinIO healthy

param(
    [string]$BaseUrl = "http://localhost:8081",
    [string]$Username = "alice",
    [string]$Password = "password123",
    [string]$ConversationId = "550e8400-e29b-41d4-a716-446655440000",
    [int]$FileSizeBytes = 1048576  # 1 MB
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "LAYER 2 FILE UPLOAD - VALIDATION SCRIPT" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# ============================================
# HELPER FUNCTIONS
# ============================================

function Write-TestHeader {
    param([string]$TestName)
    Write-Host ""
    Write-Host ">>> TEST: $TestName" -ForegroundColor Yellow
}

function Write-Success {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

function Write-Failure {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor Red
}

function Get-JwtToken {
    param(
        [string]$Url,
        [string]$User,
        [string]$Pass
    )
    
    Write-TestHeader "0. Obter JWT Token"
    
    $loginBody = @{
        username = $User
        password = $Pass
    } | ConvertTo-Json
    
    try {
        $response = Invoke-RestMethod -Uri "$Url/api/auth/login" `
            -Method Post `
            -ContentType "application/json" `
            -Body $loginBody
        
        # Response shape: { token, tokenType, expiresIn, user: { userId, username, role } }
        $username = $response.user.username
        $userId = $response.user.userId

        Write-Success "Autenticado como $username"
        Write-Host "   UserId: $userId" -ForegroundColor Gray
        Write-Host "   Token: $($response.token.Substring(0, 50))..." -ForegroundColor Gray

        # Expor o userId globalmente para incluir em requests que exigem senderId
        $global:UserId = $userId

        return $response.token
    }
    catch {
        Write-Failure "Falha na autenticação: $_"
        throw
    }
}

function New-TestFile {
    param([int]$SizeBytes, [string]$OutputPath)
    
    Write-Host "📄 Criando arquivo de teste ($SizeBytes bytes)..." -ForegroundColor Cyan
    
    $content = [byte[]]::new($SizeBytes)
    (New-Object Random).NextBytes($content)
    [System.IO.File]::WriteAllBytes($OutputPath, $content)
    
    # Calcular MD5
    $md5 = [System.Security.Cryptography.MD5]::Create()
    $hash = $md5.ComputeHash([System.IO.File]::ReadAllBytes($OutputPath))
    $md5String = [System.BitConverter]::ToString($hash).Replace("-", "").ToLower()
    
    Write-Success "Arquivo criado: $OutputPath"
    Write-Host "   MD5: $md5String" -ForegroundColor Gray
    
    return $md5String
}

function Test-InitiateUpload {
    param(
        [string]$Url,
        [string]$Token,
        [string]$ConvId,
        [string]$Filename,
        [int]$Size,
        [string]$MimeType
    )
    
    Write-TestHeader "1. Iniciar Upload"
    
    $headers = @{
        "Authorization" = "Bearer $Token"
        "Content-Type" = "application/json"
    }
    
    $body = @{
        conversationId = $ConvId
        filename = $Filename
        sizeBytes = $Size
        mimeType = $MimeType
    } | ConvertTo-Json
    
    try {
        $response = Invoke-RestMethod -Uri "$Url/api/files/initiate" `
            -Method Post `
            -Headers $headers `
            -Body $body
        
        Write-Success "Upload iniciado"
        Write-Host "   FileId: $($response.fileId)" -ForegroundColor Gray
        Write-Host "   Total Chunks: $($response.totalChunks)" -ForegroundColor Gray
        Write-Host "   Upload expira em: $($response.uploadExpiresAt)" -ForegroundColor Gray
        
        return $response
    }
    catch {
        Write-Failure "Falha ao iniciar upload: $_"
        throw
    }
}

function Test-UploadToMinio {
    param(
        [string]$UploadUrl,
        [string]$FilePath,
        [string]$MimeType
    )
    
    Write-TestHeader "2. Upload para MinIO"
    
    try {
        $fileBytes = [System.IO.File]::ReadAllBytes($FilePath)
        
        # Use WebRequest for better compatibility with S3 pre-signed URLs
        $webRequest = [System.Net.WebRequest]::Create($UploadUrl)
        $webRequest.Method = "PUT"
        $webRequest.ContentType = $MimeType
        $webRequest.ContentLength = $fileBytes.Length
        
        $requestStream = $webRequest.GetRequestStream()
        $requestStream.Write($fileBytes, 0, $fileBytes.Length)
        $requestStream.Close()
        
        $response = $webRequest.GetResponse()
        $statusCode = [int]$response.StatusCode
        $response.Close()
        
        if ($statusCode -eq 200) {
            Write-Success "Arquivo enviado para MinIO"
            Write-Host "   URL: $($UploadUrl.Substring(0, 60))..." -ForegroundColor Gray
        }
        else {
            throw "HTTP $statusCode"
        }
    }
    catch {
        Write-Failure "Falha no upload para MinIO: $_"
        throw
    }
}

function Test-CompleteUpload {
    param(
        [string]$Url,
        [string]$Token,
        [string]$FileId,
        [string]$Checksum
    )
    
    Write-TestHeader "3. Completar Upload"
    
    $headers = @{
        "Authorization" = "Bearer $Token"
        "Content-Type" = "application/json"
    }
    
    $body = @{
        fileId = $FileId
        checksumMd5 = $Checksum
        senderId = $global:UserId
        recipientIds = @("b2b2b2b2-2222-2222-2222-222222222222")
    } | ConvertTo-Json
    
    try {
        $response = Invoke-RestMethod -Uri "$Url/api/files/complete" `
            -Method Post `
            -Headers $headers `
            -Body $body
        
        Write-Success "Upload marcado como COMPLETED"
        Write-Host "   Status: $($response.uploadStatus)" -ForegroundColor Gray
        Write-Host "   UploadedAt: $($response.uploadedAt)" -ForegroundColor Gray
        
        return $response
    }
    catch {
        Write-Failure "Falha ao completar upload: $_"
        throw
    }
}

function Test-GenerateDownloadUrl {
    param(
        [string]$Url,
        [string]$Token,
        [string]$FileId
    )
    
    Write-TestHeader "4. Gerar URL de Download"
    
    $headers = @{
        "Authorization" = "Bearer $Token"
    }
    
    try {
        $response = Invoke-RestMethod -Uri "$Url/api/files/$FileId/download" `
            -Method Get `
            -Headers $headers
        
        Write-Success "Download URL gerada"
        Write-Host "   Expira em: $($response.downloadExpiresAt)" -ForegroundColor Gray
        Write-Host "   Filename: $($response.filename)" -ForegroundColor Gray
        
        return $response
    }
    catch {
        Write-Failure "Falha ao gerar download URL: $_"
        throw
    }
}

function Test-DownloadFromMinio {
    param(
        [string]$DownloadUrl,
        [string]$OutputPath
    )
    
    Write-TestHeader "5. Download do MinIO"
    
    try {
        # Use WebClient for better compatibility with S3 pre-signed URLs
        $webClient = New-Object System.Net.WebClient
        $webClient.DownloadFile($DownloadUrl, $OutputPath)
        $webClient.Dispose()
        
        Write-Success "Arquivo baixado"
        Write-Host "   Path: $OutputPath" -ForegroundColor Gray
        
        # Calcular MD5
        $md5 = [System.Security.Cryptography.MD5]::Create()
        $hash = $md5.ComputeHash([System.IO.File]::ReadAllBytes($OutputPath))
        $md5String = [System.BitConverter]::ToString($hash).Replace("-", "").ToLower()
        
        Write-Host "   MD5: $md5String" -ForegroundColor Gray
        
        return $md5String
    }
    catch {
        Write-Failure "Falha no download do MinIO: $_"
        throw
    }
}

function Test-ValidationRules {
    param(
        [string]$Url,
        [string]$Token,
        [string]$ConvId
    )
    
    Write-TestHeader "6. Validações de Regras de Negócio"
    
    # Test 6.1: Arquivo > 2 GB
    Write-Host ""
    Write-Host "   Teste 6.1: Arquivo > 2 GB (deve rejeitar)" -ForegroundColor Cyan
    
    $headers = @{
        "Authorization" = "Bearer $Token"
        "Content-Type" = "application/json"
    }
    
    $body = @{
        conversationId = $ConvId
        filename = "huge-file.iso"
        sizeBytes = 2147483649  # 2 GB + 1 byte
        mimeType = "application/octet-stream"
    } | ConvertTo-Json
    
    try {
        Invoke-RestMethod -Uri "$Url/api/files/initiate" `
            -Method Post `
            -Headers $headers `
            -Body $body `
            -ErrorAction Stop
        
        Write-Failure "   Arquivo > 2 GB NÃO foi rejeitado!"
    }
    catch {
        if ($_.Exception.Response.StatusCode -eq 400) {
            Write-Success "   Arquivo > 2 GB rejeitado corretamente (400 Bad Request)"
        }
        else {
            Write-Failure "   Status inesperado: $($_.Exception.Response.StatusCode)"
        }
    }
    
    # Test 6.2: Sem JWT Token
    Write-Host ""
    Write-Host "   Teste 6.2: Sem JWT Token (deve retornar 401)" -ForegroundColor Cyan
    
    try {
        Invoke-RestMethod -Uri "$Url/api/files/initiate" `
            -Method Post `
            -ContentType "application/json" `
            -Body $body `
            -ErrorAction Stop
        
        Write-Failure "   Endpoint SEM JWT NÃO foi bloqueado!"
    }
    catch {
        if ($_.Exception.Response.StatusCode -eq 401) {
            Write-Success "   Acesso sem JWT bloqueado (401 Unauthorized)"
        }
        else {
            Write-Failure "   Status inesperado: $($_.Exception.Response.StatusCode)"
        }
    }
}

# ============================================
# MAIN EXECUTION
# ============================================

try {
    # Preparar diretório temporário
    $tempDir = "C:\temp"
    if (-not (Test-Path $tempDir)) {
        New-Item -ItemType Directory -Path $tempDir | Out-Null
    }
    
    $testFilePath = Join-Path $tempDir "test-upload.pdf"
    $downloadedFilePath = Join-Path $tempDir "test-downloaded.pdf"
    
    # STEP 0: Autenticar
    $jwtToken = Get-JwtToken -Url $BaseUrl -User $Username -Pass $Password
    
    # STEP 1: Criar arquivo de teste
    $originalMd5 = New-TestFile -SizeBytes $FileSizeBytes -OutputPath $testFilePath
    
    # STEP 2: Iniciar upload
    $initiateResponse = Test-InitiateUpload `
        -Url $BaseUrl `
        -Token $jwtToken `
        -ConvId $ConversationId `
        -Filename "test-upload.pdf" `
        -Size $FileSizeBytes `
        -MimeType "application/pdf"
    
    Write-Host ""
    Write-Host "DEBUG: Upload URL = $($initiateResponse.uploadUrl)" -ForegroundColor Magenta
    Write-Host ""
    
    # STEP 3: Upload para MinIO
    Test-UploadToMinio `
        -UploadUrl $initiateResponse.uploadUrl `
        -FilePath $testFilePath `
        -MimeType "application/pdf"
    
    # STEP 4: Completar upload
    $completeResponse = Test-CompleteUpload `
        -Url $BaseUrl `
        -Token $jwtToken `
        -FileId $initiateResponse.fileId `
        -Checksum $originalMd5
    
    # STEP 5: Gerar download URL
    $downloadResponse = Test-GenerateDownloadUrl `
        -Url $BaseUrl `
        -Token $jwtToken `
        -FileId $initiateResponse.fileId
    
    # STEP 6: Download do MinIO
    $downloadedMd5 = Test-DownloadFromMinio `
        -DownloadUrl $downloadResponse.downloadUrl `
        -OutputPath $downloadedFilePath
    
    # STEP 7: Validar integridade
    Write-TestHeader "7. Validar Integridade"
    
    if ($originalMd5 -eq $downloadedMd5) {
        Write-Success "Checksums coincidem! Upload/Download bem-sucedidos"
        Write-Host "   Original:   $originalMd5" -ForegroundColor Gray
        Write-Host "   Downloaded: $downloadedMd5" -ForegroundColor Gray
    }
    else {
        Write-Failure "Checksums DIFERENTES! Arquivo corrompido"
        Write-Host "   Original:   $originalMd5" -ForegroundColor Red
        Write-Host "   Downloaded: $downloadedMd5" -ForegroundColor Red
        throw "Checksum mismatch"
    }
    
    # STEP 8: Testes de validação
    Test-ValidationRules -Url $BaseUrl -Token $jwtToken -ConvId $ConversationId
    
    # RESUMO FINAL
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "TODOS OS TESTES PASSARAM! ✅" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Layer 2 (File Upload/Download) validado com sucesso!" -ForegroundColor Green
    Write-Host "Próximo passo: Implementar Layer 3 (Platform Connectors Mock)" -ForegroundColor Yellow
    Write-Host ""
    
    # Cleanup
    Write-Host "Limpando arquivos temporários..." -ForegroundColor Gray
    Remove-Item $testFilePath -ErrorAction SilentlyContinue
    Remove-Item $downloadedFilePath -ErrorAction SilentlyContinue
    
    exit 0
}
catch {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "FALHA NOS TESTES ❌" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Erro: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Stack Trace:" -ForegroundColor Gray
    Write-Host $_.ScriptStackTrace -ForegroundColor Gray
    Write-Host ""
    
    exit 1
}
