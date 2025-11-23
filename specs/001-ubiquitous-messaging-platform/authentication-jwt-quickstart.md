# Quickstart: Autenticação JWT

## 🚀 Setup Rápido

```powershell
# 1. Adicionar dependências JWT ao pom.xml
mvn clean package -DskipTests

# 2. Configurar secret JWT
# Editar src/main/resources/application.properties
jwt.secret=minha-chave-super-secreta-deve-ter-pelo-menos-256-bits
jwt.expiration=86400000

# 3. Iniciar aplicação
.\start.ps1
```

---

## 🔐 Testar Autenticação

### Passo 1: Fazer Login (HTTP)

**Endpoint**: `POST http://localhost:8081/api/auth/login`

**Request (Postman HTTP)**:
```json
{
  "username": "alice",
  "password": "password123"
}
```

**Response 200 OK**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhMWExYTFhMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJ1c2VybmFtZSI6ImFsaWNlIiwicm9sZSI6IlJPTEVfVVNFUiIsImlhdCI6MTczMjM5MjAwMCwiZXhwIjoxNzMyNDc4NDAwfQ.signature",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "user": {
    "userId": "a1a1a1a1-1111-1111-1111-111111111111",
    "username": "alice",
    "role": "ROLE_USER"
  }
}
```

**⚠️ COPIE O TOKEN** - você vai precisar dele para os próximos requests!

---

### Passo 2: Usar Token em Request gRPC

**Método**: `ChatService/SendMessage`  
**URL**: `localhost:9090` (desmarcar TLS)

**Metadata (Postman gRPC)**:
```
Key: authorization
Value: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Payload**:
```json
{
  "conversation_id": "3184a104-6171-45d5-b541-7ee8f36d1062",
  "sender_id": "a1a1a1a1-1111-1111-1111-111111111111",
  "recipient_id": "b2b2b2b2-2222-2222-2222-222222222222",
  "message_id": "00000001-0000-0000-0000-000000000001",
  "message_text": "Mensagem autenticada!"
}
```

**Response ✅** (com token válido):
```json
{
  "messageId": "00000001-0000-0000-0000-000000000001",
  "timestamp": "2025-11-23T20:00:00.000Z",
  "sequenceNumber": "1"
}
```

**Response ❌** (SEM token):
```
Code: UNAUTHENTICATED
Message: Authorization header missing
```

**Response ❌** (sender_id diferente do token):
```
Code: PERMISSION_DENIED
Message: Cannot send message as another user
```

---

## 👥 Usuários Disponíveis (POC)

| Username | Password | User ID | Role |
|----------|----------|---------|------|
| alice | password123 | a1a1a1a1-1111-1111-1111-111111111111 | ROLE_USER |
| bob | password123 | b2b2b2b2-2222-2222-2222-222222222222 | ROLE_USER |
| admin | admin123 | 00000000-0000-0000-0000-000000000000 | ROLE_ADMIN |

---

## 🧪 Cenários de Teste

### Cenário 1: Login Bem-Sucedido

```bash
# Request
POST http://localhost:8081/api/auth/login
Content-Type: application/json

{
  "username": "alice",
  "password": "password123"
}

# Expected Response: 200 OK com token JWT
```

---

### Cenário 2: Login Falhou (credenciais inválidas)

```bash
# Request
POST http://localhost:8081/api/auth/login
Content-Type: application/json

{
  "username": "alice",
  "password": "senha-errada"
}

# Expected Response: 401 Unauthorized
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password",
  "timestamp": "2025-11-23T20:00:00.000Z"
}
```

---

### Cenário 3: Request gRPC COM Token

```bash
# grpcurl
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -d '{
    "conversation_id": "3184a104-6171-45d5-b541-7ee8f36d1062",
    "sender_id": "a1a1a1a1-1111-1111-1111-111111111111",
    "recipient_id": "b2b2b2b2-2222-2222-2222-222222222222",
    "message_id": "00000001-0000-0000-0000-000000000001",
    "message_text": "Mensagem autenticada"
  }' \
  localhost:9090 chat.ChatService/SendMessage

# Expected: Success (200 OK)
```

---

### Cenário 4: Request gRPC SEM Token

```bash
# grpcurl (SEM header authorization)
grpcurl -plaintext \
  -d '{"conversation_id": "..."}' \
  localhost:9090 chat.ChatService/SendMessage

# Expected: UNAUTHENTICATED
# Message: "Authorization header missing"
```

---

### Cenário 5: Token Expirado

```bash
# Esperar 24 horas OU configurar jwt.expiration=5000 (5 segundos)

# Request com token expirado
grpcurl -plaintext \
  -H "authorization: Bearer <token-expirado>" \
  -d '{"conversation_id": "..."}' \
  localhost:9090 chat.ChatService/SendMessage

# Expected: UNAUTHENTICATED
# Message: "Token expired"
```

---

### Cenário 6: Tentar Enviar Mensagem Como Outro Usuário

```bash
# 1. Login como Alice
POST /api/auth/login
{"username": "alice", "password": "password123"}
→ Recebe token de Alice

# 2. Tentar enviar mensagem com sender_id=bob (mas token é de Alice)
grpcurl -plaintext \
  -H "authorization: Bearer <token-alice>" \
  -d '{
    "sender_id": "b2b2b2b2-2222-2222-2222-222222222222",
    "message_text": "Tentando se passar por Bob"
  }' \
  localhost:9090 chat.ChatService/SendMessage

# Expected: PERMISSION_DENIED
# Message: "Cannot send message as another user"
```

---

## 📋 Checklist de Validação

- [ ] Login com credenciais válidas retorna token JWT
- [ ] Login com credenciais inválidas retorna 401
- [ ] Request gRPC SEM token retorna UNAUTHENTICATED
- [ ] Request gRPC COM token válido é processado normalmente
- [ ] Request gRPC COM token expirado retorna UNAUTHENTICATED
- [ ] sender_id diferente do userId no token retorna PERMISSION_DENIED
- [ ] Token pode ser decodificado em https://jwt.io e mostra claims corretos

---

## 🔍 Debugging

### Ver Conteúdo do Token

Copie o token e cole em https://jwt.io

**Header**:
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

**Payload**:
```json
{
  "sub": "a1a1a1a1-1111-1111-1111-111111111111",
  "username": "alice",
  "role": "ROLE_USER",
  "iat": 1732392000,
  "exp": 1732478400
}
```

---

### Logs da Aplicação

```bash
# Ver logs do interceptor JWT
Get-Content logs/app.log | Select-String "JWT"

# Ver tentativas de login
Get-Content logs/app.log | Select-String "login"
```

---

### Testar Secret JWT

```powershell
# Ver secret configurado
Get-Content src/main/resources/application.properties | Select-String "jwt.secret"

# Verificar que secret tem >256 bits (32 caracteres)
$secret = "minha-chave-super-secreta-deve-ter-pelo-menos-256-bits"
$secret.Length  # Deve ser >= 32
```

---

## 🚨 Troubleshooting

### Erro: "Authorization header missing"

**Causa**: Token não foi incluído no metadata gRPC  
**Solução**: 
- Postman: Metadata tab → Add `authorization: Bearer <token>`
- grpcurl: Adicionar `-H "authorization: Bearer <token>"`

---

### Erro: "Invalid token signature"

**Causa**: Secret JWT diferente entre geração e validação  
**Solução**:
1. Verificar `jwt.secret` em application.properties
2. Reiniciar aplicação
3. Fazer login novamente para gerar novo token

---

### Erro: "Token expired"

**Causa**: Token passou de 24 horas (padrão)  
**Solução**: Fazer login novamente para obter novo token

---

### Erro: "Cannot send message as another user"

**Causa**: sender_id no payload diferente do userId no token  
**Solução**: Usar o mesmo userId que aparece no token payload

---

## 📚 Próximos Passos

### P2: Migrar para Produção

1. **Adicionar MongoDB UserRepository**
   ```bash
   # Criar collection users
   docker exec -it mongodb-dev mongosh -u admin -p password
   use chat
   db.createCollection("users")
   db.users.createIndex({"user_id": 1}, {unique: true})
   db.users.createIndex({"username": 1}, {unique: true})
   ```

2. **Adicionar BCrypt Password Hashing**
   ```xml
   <dependency>
       <groupId>org.springframework.security</groupId>
       <artifactId>spring-security-crypto</artifactId>
   </dependency>
   ```

3. **Mover Secret para Environment Variable**
   ```powershell
   $env:JWT_SECRET = "producao-secret-aleatorio-256-bits"
   ```

4. **Adicionar Rate Limiting**
   ```java
   @RateLimiter(name = "loginLimiter", fallbackMethod = "loginFallback")
   public LoginResponse login(LoginRequest request) { ... }
   ```

5. **Implementar Refresh Token**
   - Token de acesso: 1 hora
   - Refresh token: 7 dias
   - Endpoint POST /api/auth/refresh

---

## 📖 Referências

- JWT Introduction: https://jwt.io/introduction
- JJWT Library: https://github.com/jwtk/jjwt
- Spring Security: https://spring.io/projects/spring-security
- gRPC Metadata: https://grpc.io/docs/guides/metadata/
