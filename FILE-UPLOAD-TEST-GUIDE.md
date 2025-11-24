# 📤 Guia de Testes - File Upload/Download (Layer 2)

## ✅ VALIDAÇÃO DA IMPLEMENTAÇÃO

### 🔍 Componentes Implementados

| Componente | Status | Arquivo | Tamanho |
|------------|--------|---------|---------|
| **FileMetadata Entity** | ✅ Compilado | `FileMetadata.class` | 9762 bytes |
| **FileStorageService** | ✅ Compilado | `FileStorageService.class` | 9922 bytes |
| **MinioConfig** | ✅ Compilado | `MinioConfig.class` | 2495 bytes |
| **FileController** | ✅ Compilado | `FileController.class` | 9184 bytes (com JWT) |
| **DTOs (5 classes)** | ✅ Compilado | Request/Response classes | ~25 KB total |
| **MinIO Container** | ✅ Rodando | Docker | Healthy (39+ min uptime) |

### 🔐 JWT Configurado
- ✅ `JwtService.java` implementado
- ✅ Secret key configurada: `jwt.secret=chat-api-super-secret-key...`
- ✅ Expiração: 24 horas (86400000 ms)

---

## 🚀 PRÉ-REQUISITOS PARA TESTES

### 1. Iniciar Spring Boot
```bash
cd C:\Users\marcos.pereira\Desktop\programacao\java\chat\chat
mvn spring-boot:run -Dmaven.test.skip=true
```

**Aguarde até ver**:
```
Started ChatApiApplication in X.XXX seconds
gRPC Server started, listening on port 9090
Tomcat started on port 8081 (http)
MinIO client initialized successfully
```

### 2. Verificar MinIO
**Console Web**: http://localhost:9001
- **Usuário**: minioadmin
- **Senha**: minioadmin
- **Bucket**: `chat-files` (criado automaticamente pelo app)

**API**: http://localhost:9000/minio/health/live
- Deve retornar status 200 OK

### 3. Obter Token JWT
**Endpoint**: `POST http://localhost:8081/api/auth/login`

**⚠️ USUÁRIOS PRÉ-CADASTRADOS** (POC - in-memory):
| Username | Password | Role | UserId |
|----------|----------|------|--------|
| **alice** | password123 | ROLE_USER | a1a1a1a1-1111-1111-1111-111111111111 |
| **bob** | password123 | ROLE_USER | b2b2b2b2-2222-2222-2222-222222222222 |
| **admin** | admin123 | ROLE_ADMIN | 00000000-0000-0000-0000-000000000000 |

**Request Body**:
```json
{
  "username": "alice",
  "password": "password123"
}
```

**Response**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6ImFsaWNlIiwicm9sZSI6IlJPTEVfVVNFUiIsInN1YiI6ImExYTFhMWExLTExMTEtMTExMS0xMTExLTExMTExMTExMTExMSIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoxNzAwMDg2NDAwfQ.signature",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "user": {
    "userId": "a1a1a1a1-1111-1111-1111-111111111111",
    "username": "alice",
    "role": "ROLE_USER"
  }
}
```

**⚠️ IMPORTANTE**: Copie o valor do campo `token` para usar nos próximos testes.

---

## 📝 CENÁRIOS DE TESTE

### 🧪 TESTE 1: Iniciar Upload de Arquivo Pequeno (1 MB)

**Objetivo**: Validar fluxo de inicialização e geração de pre-signed URL.

#### Request
```http
POST http://localhost:8081/api/files/initiate
Authorization: Bearer <SEU_TOKEN_JWT>
Content-Type: application/json

{
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "filename": "test-document.pdf",
  "sizeBytes": 1048576,
  "mimeType": "application/pdf"
}
```

#### Response Esperado (201 Created)
```json
{
  "fileId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "uploadUrl": "http://localhost:9000/chat-files/f47ac10b-58cc-4372-a567-0e02b2c3d479?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=...",
  "uploadExpiresAt": "2025-11-23T23:54:31Z",
  "chunkSizeBytes": 5242880,
  "totalChunks": 1,
  "resumable": true
}
```

#### Validações
- ✅ HTTP 201 Created
- ✅ `fileId` é UUID válido
- ✅ `uploadUrl` contém assinatura AWS (X-Amz-*)
- ✅ `uploadExpiresAt` é 1 hora no futuro
- ✅ `totalChunks` = ceil(1048576 / 5242880) = 1

#### MongoDB - Verificar Metadata
```javascript
db.file_metadata.findOne({fileId: "f47ac10b-58cc-4372-a567-0e02b2c3d479"})
```

**Esperado**:
```json
{
  "_id": ObjectId("..."),
  "fileId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "filename": "test-document.pdf",
  "sizeBytes": 1048576,
  "mimeType": "application/pdf",
  "uploadStatus": "INITIATED",
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "uploaderId": "12345678-90ab-cdef-1234-567890abcdef",
  "chunksUploaded": 0,
  "totalChunks": 1,
  "_class": "com.chat.model.FileMetadata"
}
```

---

### 🧪 TESTE 2: Upload Real do Arquivo para MinIO

**Objetivo**: Fazer PUT direto no MinIO usando pre-signed URL.

#### Preparar Arquivo de Teste
**PowerShell**:
```powershell
# Criar arquivo PDF de teste (1 MB)
$content = [byte[]]::new(1048576)
(New-Object Random).NextBytes($content)
[System.IO.File]::WriteAllBytes("C:\temp\test-document.pdf", $content)

# Calcular MD5
$md5 = [System.Security.Cryptography.MD5]::Create()
$hash = $md5.ComputeHash([System.IO.File]::ReadAllBytes("C:\temp\test-document.pdf"))
$md5String = [System.BitConverter]::ToString($hash).Replace("-", "").ToLower()
Write-Host "MD5: $md5String"
```

#### Upload via cURL
```bash
curl -X PUT \
  "<UPLOAD_URL_DO_TESTE_1>" \
  -H "Content-Type: application/pdf" \
  -H "Content-MD5: <MD5_BASE64>" \
  --data-binary "@C:/temp/test-document.pdf"
```

**OU via PowerShell**:
```powershell
$uploadUrl = "<UPLOAD_URL_DO_TESTE_1>"
$filePath = "C:\temp\test-document.pdf"

Invoke-RestMethod -Uri $uploadUrl -Method Put -InFile $filePath -ContentType "application/pdf"
```

#### Response Esperado
- HTTP 200 OK (sem body)

#### Validar no MinIO Console
1. Acessar http://localhost:9001
2. Login: minioadmin / minioadmin
3. Bucket: `chat-files`
4. Verificar arquivo com nome = `fileId` do TESTE 1

---

### 🧪 TESTE 3: Completar Upload

**Objetivo**: Marcar upload como concluído e validar checksum.

#### Request
```http
POST http://localhost:8081/api/files/complete
Authorization: Bearer <SEU_TOKEN_JWT>
Content-Type: application/json

{
  "fileId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "checksumMd5": "<MD5_DO_ARQUIVO_CALCULADO_NO_TESTE_2>"
}
```

#### Response Esperado (200 OK)
```json
{
  "fileId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "uploadStatus": "COMPLETED",
  "filename": "test-document.pdf",
  "sizeBytes": 1048576,
  "mimeType": "application/pdf",
  "uploadedAt": "2025-11-23T22:54:31Z",
  "conversationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### Validações
- ✅ HTTP 200 OK
- ✅ `uploadStatus` = "COMPLETED"
- ✅ `uploadedAt` preenchido com timestamp atual

#### MongoDB - Verificar Atualização
```javascript
db.file_metadata.findOne({fileId: "f47ac10b-58cc-4372-a567-0e02b2c3d479"})
```

**Campo atualizado**:
```json
{
  "uploadStatus": "COMPLETED",
  "uploadedAt": ISODate("2025-11-23T22:54:31.000Z"),
  "checksumMd5": "<MD5_CALCULADO>"
}
```

---

### 🧪 TESTE 4: Gerar URL de Download

**Objetivo**: Obter pre-signed URL para download seguro.

#### Request
```http
GET http://localhost:8081/api/files/f47ac10b-58cc-4372-a567-0e02b2c3d479/download
Authorization: Bearer <SEU_TOKEN_JWT>
```

#### Response Esperado (200 OK)
```json
{
  "fileId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "downloadUrl": "http://localhost:9000/chat-files/f47ac10b-58cc-4372-a567-0e02b2c3d479?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=...",
  "downloadExpiresAt": "2025-11-23T23:54:31Z",
  "filename": "test-document.pdf",
  "sizeBytes": 1048576,
  "mimeType": "application/pdf"
}
```

#### Validações
- ✅ HTTP 200 OK
- ✅ `downloadUrl` contém assinatura válida
- ✅ `downloadExpiresAt` é 1 hora no futuro

#### Download Real
```bash
curl -o downloaded-file.pdf "<DOWNLOAD_URL>"
```

**OU abrir diretamente no navegador** (URL expira em 1h):
```
<DOWNLOAD_URL>
```

#### Comparar Checksums
```powershell
# Original
$original = Get-FileHash "C:\temp\test-document.pdf" -Algorithm MD5
# Downloaded
$downloaded = Get-FileHash "downloaded-file.pdf" -Algorithm MD5

Write-Host "Original:   $($original.Hash)"
Write-Host "Downloaded: $($downloaded.Hash)"
# Devem ser IGUAIS
```

---

### 🧪 TESTE 5: Upload de Arquivo Grande (10 MB - Resumable)

**Objetivo**: Validar protocolo resumável com múltiplos chunks.

#### Request Initiate
```json
{
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "filename": "large-video.mp4",
  "sizeBytes": 10485760,
  "mimeType": "video/mp4"
}
```

#### Response Esperado
```json
{
  "fileId": "...",
  "uploadUrl": "...",
  "chunkSizeBytes": 5242880,
  "totalChunks": 2,
  "resumable": true
}
```

**Observar**: `totalChunks` = ceil(10485760 / 5242880) = **2 chunks**

#### Upload em Chunks (Simulação)
**Chunk 1** (0-5242879 bytes):
```powershell
$chunk1 = [System.IO.File]::ReadAllBytes("C:\temp\large-video.mp4")[0..5242879]
[System.IO.File]::WriteAllBytes("C:\temp\chunk1.bin", $chunk1)
Invoke-RestMethod -Uri $uploadUrl -Method Put -InFile "C:\temp\chunk1.bin"
```

**Chunk 2** (5242880-10485759 bytes):
```powershell
$chunk2 = [System.IO.File]::ReadAllBytes("C:\temp\large-video.mp4")[5242880..10485759]
[System.IO.File]::WriteAllBytes("C:\temp\chunk2.bin", $chunk2)
# Continuar upload...
```

**⚠️ NOTA**: MinIO pre-signed URL espera upload completo de uma vez. Para resumable real, seria necessário implementar Multipart Upload (fora do escopo do POC).

---

### 🧪 TESTE 6: Validação de Tamanho Máximo (2 GB)

**Objetivo**: Verificar rejeição de arquivos > 2 GB.

#### Request
```json
{
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "filename": "huge-file.iso",
  "sizeBytes": 2147483649,
  "mimeType": "application/octet-stream"
}
```

#### Response Esperado (400 Bad Request)
```json
{
  "timestamp": "2025-11-23T22:54:31Z",
  "status": 400,
  "error": "Bad Request",
  "message": "File size exceeds maximum allowed (2147483648 bytes)",
  "path": "/api/files/initiate"
}
```

---

### 🧪 TESTE 7: Autenticação JWT Inválida

**Objetivo**: Verificar proteção dos endpoints.

#### Request (sem token)
```http
POST http://localhost:8081/api/files/initiate
Content-Type: application/json

{
  "conversationId": "...",
  "filename": "test.txt",
  "sizeBytes": 100,
  "mimeType": "text/plain"
}
```

#### Response Esperado (401 Unauthorized)
```json
{
  "timestamp": "2025-11-23T22:54:31Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Missing Authorization header",
  "path": "/api/files/initiate"
}
```

---

## 📊 CHECKLIST DE VALIDAÇÃO COMPLETA

### ✅ Infraestrutura
- [ ] MinIO rodando na porta 9000/9001 (healthy)
- [ ] MongoDB conectado e acessível
- [ ] Spring Boot iniciado sem erros
- [ ] Bucket `chat-files` criado automaticamente

### ✅ Endpoints REST
- [ ] POST /api/files/initiate retorna 201 + uploadUrl
- [ ] POST /api/files/complete retorna 200 + COMPLETED status
- [ ] GET /api/files/{id}/download retorna 200 + downloadUrl
- [ ] Todos endpoints requerem JWT (401 sem token)

### ✅ Persistência MongoDB
- [ ] FileMetadata criado com status INITIATED
- [ ] Status atualizado para COMPLETED após upload
- [ ] Checksum MD5 armazenado corretamente
- [ ] uploadedAt timestamp preenchido

### ✅ MinIO Storage
- [ ] Arquivo salvo no bucket com nome = fileId
- [ ] Pre-signed URLs funcionam (PUT e GET)
- [ ] URLs expiram após 1 hora
- [ ] Download preserva integridade (MD5 igual)

### ✅ Validações de Negócio
- [ ] Arquivos > 2 GB são rejeitados
- [ ] Checksum MD5 é validado
- [ ] Conversação UUID tem formato correto
- [ ] uploaderId extraído do JWT corretamente

---

## 🐛 TROUBLESHOOTING

### Problema: "MinIO client initialization failed"
**Solução**: Verificar docker-compose e reiniciar MinIO
```bash
docker-compose restart minio
docker logs minio
```

### Problema: "JWT token invalid"
**Solução**: Gerar novo token via `/api/auth/login`
```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123"}'
```

### Problema: "Pre-signed URL expired"
**Solução**: URLs expiram em 1h. Regenerar via `/api/files/initiate` ou `/download`

### Problema: "Checksum mismatch"
**Solução**: Recalcular MD5 do arquivo enviado
```powershell
Get-FileHash "arquivo.pdf" -Algorithm MD5
```

---

## 📈 PRÓXIMOS PASSOS (Layer 3 - Platform Connectors Mock)

Após validar todos os testes acima:

1. ✅ **T053-T057**: File Upload/Download completo
2. ⏭️ **T070-T072**: Implementar LinkedAccount entity + PlatformAdapter interface
3. ⏭️ **T073-T074**: Criar WhatsApp/Instagram mock adapters
4. ⏭️ **T075-T078**: Integrar com MessageService + Kafka routing

---

## 📚 REFERÊNCIAS

- **Spec**: `specs/001-ubiquitous-messaging-platform/spec.md` (FR-023, FR-024)
- **MinIO Docs**: https://min.io/docs/minio/linux/developers/java/API.html
- **Pre-signed URLs**: https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html
