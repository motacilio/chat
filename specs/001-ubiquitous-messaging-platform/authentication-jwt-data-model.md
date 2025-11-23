# Data Model: Autenticação JWT

## Entities

### User (POC - In-Memory)

```java
public class User {
    private String userId;        // UUID format: "a1a1a1a1-1111-1111-1111-111111111111"
    private String username;      // Unique, usado para login
    private String password;      // Plain text (POC only), será BCrypt em produção
    private String role;          // "ROLE_USER" ou "ROLE_ADMIN"
    private Instant createdAt;    // Timestamp de criação
}
```

**POC Users** (hardcoded em `AuthenticationService`):
```java
private static final Map<String, User> USERS = Map.of(
    "alice", new User(
        "a1a1a1a1-1111-1111-1111-111111111111",
        "alice",
        "password123",
        "ROLE_USER",
        Instant.now()
    ),
    "bob", new User(
        "b2b2b2b2-2222-2222-2222-222222222222",
        "bob",
        "password123",
        "ROLE_USER",
        Instant.now()
    ),
    "admin", new User(
        "00000000-0000-0000-0000-000000000000",
        "admin",
        "admin123",
        "ROLE_ADMIN",
        Instant.now()
    )
);
```

---

### JwtToken (Transient - Not Persisted)

```java
public class JwtToken {
    private String token;         // JWT string (formato: header.payload.signature)
    private String tokenType;     // "Bearer"
    private long expiresIn;       // Milissegundos até expiração
}
```

**Formato do Token JWT**:
```
Header:
{
  "alg": "HS256",
  "typ": "JWT"
}

Payload:
{
  "sub": "a1a1a1a1-1111-1111-1111-111111111111",  // userId
  "username": "alice",
  "role": "ROLE_USER",
  "iat": 1700000000,  // Issued at (timestamp)
  "exp": 1700086400   // Expiration (timestamp)
}

Signature:
HMACSHA256(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  secret
)
```

---

## DTOs

### LoginRequest

```java
public record LoginRequest(
    @NotBlank(message = "Username is required")
    String username,
    
    @NotBlank(message = "Password is required")
    String password
) {}
```

### LoginResponse

```java
public record LoginResponse(
    String token,
    String tokenType,  // "Bearer"
    long expiresIn,    // Milissegundos
    UserInfo user
) {}

public record UserInfo(
    String userId,
    String username,
    String role
) {}
```

### ErrorResponse

```java
public record ErrorResponse(
    int status,
    String error,
    String message,
    Instant timestamp
) {}
```

**Exemplo de resposta de erro**:
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password",
  "timestamp": "2025-11-23T20:00:00.000Z"
}
```

---

## API Endpoints

### POST /api/auth/login

**Request**:
```json
{
  "username": "alice",
  "password": "password123"
}
```

**Response 200 OK**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "user": {
    "userId": "a1a1a1a1-1111-1111-1111-111111111111",
    "username": "alice",
    "role": "ROLE_USER"
  }
}
```

**Response 401 Unauthorized** (credenciais inválidas):
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password",
  "timestamp": "2025-11-23T20:00:00.000Z"
}
```

**Response 400 Bad Request** (validação falhou):
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Username is required",
  "timestamp": "2025-11-23T20:00:00.000Z"
}
```

---

## gRPC Metadata

### Authorization Header

Clients MUST include JWT token em gRPC metadata:

```
Key: authorization
Value: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Exemplo em Postman gRPC**:
1. Metadata tab
2. Add key: `authorization`
3. Add value: `Bearer <token_do_login>`

**Exemplo em grpcurl**:
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -d '{"conversation_id": "..."}' \
  localhost:9090 chat.ChatService/SendMessage
```

---

## gRPC Context

### Authenticated User Context

Após validação bem-sucedida, JWT interceptor adiciona `userId` ao gRPC Context:

```java
public static final Context.Key<String> USER_ID_KEY = 
    Context.key("authenticated-user-id");

// No interceptor
Context ctx = Context.current()
    .withValue(USER_ID_KEY, claims.getSubject());

// Nos services
String userId = USER_ID_KEY.get();  // "a1a1a1a1-1111-1111-1111-111111111111"
```

**Uso nos services**:
```java
@Override
public void sendMessage(SendMessageRequest request, 
                       StreamObserver<SendMessageResponse> responseObserver) {
    // Obter userId autenticado do context
    String authenticatedUserId = USER_ID_KEY.get();
    
    // Validar que sender_id no request corresponde ao usuário autenticado
    if (!request.getSenderId().equals(authenticatedUserId)) {
        responseObserver.onError(
            Status.PERMISSION_DENIED
                .withDescription("Cannot send message as another user")
                .asRuntimeException()
        );
        return;
    }
    
    // Processar mensagem...
}
```

---

## Configuration

### application.properties

```properties
# JWT Configuration
jwt.secret=${JWT_SECRET:minha-chave-super-secreta-deve-ter-pelo-menos-256-bits}
jwt.expiration=${JWT_EXPIRATION:86400000}  # 24 horas

# HTTP Server (para endpoint /api/auth/login)
server.port=8081

# gRPC Server
grpc.server.port=9090
```

**Environment Variables** (produção):
```bash
export JWT_SECRET="producao-chave-aleatoria-gerada-com-openssl-rand"
export JWT_EXPIRATION=3600000  # 1 hora em produção
```

---

## Validation Rules

### Username
- **Required**: Não pode ser vazio
- **Min Length**: 3 caracteres
- **Max Length**: 50 caracteres
- **Pattern**: Alfanumérico + underscore (regex: `^[a-zA-Z0-9_]+$`)

### Password (POC)
- **Required**: Não pode ser vazio
- **Min Length**: 8 caracteres (POC - será aumentado para 12 em produção)

### JWT Token
- **Signature**: MUST be valid (verificado com jwt.secret)
- **Expiration**: MUST NOT be expired
- **Format**: MUST follow JWT spec (3 parts separated by dots)

---

## Security Considerations

### POC Limitations (WILL BE FIXED)

⚠️ **Plain Text Passwords**: Inaceitável em produção - migrar para BCrypt  
⚠️ **Hardcoded Users**: Não escala - migrar para MongoDB  
⚠️ **Static Secret**: Deve ser environment variable  
⚠️ **No Refresh Token**: Usuário precisa re-logar após 24h  
⚠️ **No Rate Limiting**: Vulnerável a brute force  

### Production Checklist

- [ ] BCrypt password hashing (cost factor 12)
- [ ] MongoDB UserRepository com índice único em username
- [ ] JWT secret em environment variable (nunca commitar)
- [ ] Refresh token com rotation
- [ ] Rate limiting: 5 tentativas/minuto no /login
- [ ] Token blacklist para logout
- [ ] HTTPS obrigatório (TLS certificates)
- [ ] Logging de tentativas de login falhadas
- [ ] Prometheus metrics: login_attempts_total, login_failures_total

---

## Database Schema (P2 - MongoDB)

```javascript
// Collection: users
{
  "_id": ObjectId("..."),
  "user_id": "a1a1a1a1-1111-1111-1111-111111111111",  // UUID, unique index
  "username": "alice",                                  // unique index
  "password_hash": "$2a$12$abcdefgh...",               // BCrypt hash
  "role": "ROLE_USER",                                  // enum: ROLE_USER, ROLE_ADMIN
  "created_at": ISODate("2025-11-23T20:00:00.000Z"),
  "last_login": ISODate("2025-11-23T21:30:00.000Z"),
  "is_active": true                                     // soft delete
}
```

**Indexes**:
```javascript
db.users.createIndex({ "user_id": 1 }, { unique: true });
db.users.createIndex({ "username": 1 }, { unique: true });
db.users.createIndex({ "is_active": 1 });
```

---

## Testing Data

### Test Users (POC)

| Username | Password | Role | User ID |
|----------|----------|------|---------|
| alice | password123 | ROLE_USER | a1a1a1a1-1111-1111-1111-111111111111 |
| bob | password123 | ROLE_USER | b2b2b2b2-2222-2222-2222-222222222222 |
| admin | admin123 | ROLE_ADMIN | 00000000-0000-0000-0000-000000000000 |

### Test Tokens (após login)

```bash
# Alice
POST /api/auth/login
{"username": "alice", "password": "password123"}
→ token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhMWExYTFhMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJ1c2VybmFtZSI6ImFsaWNlIiwicm9sZSI6IlJPTEVfVVNFUiIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoxNzAwMDg2NDAwfQ.signature

# Bob
POST /api/auth/login
{"username": "bob", "password": "password123"}
→ token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJiMmIyYjJiMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJ1c2VybmFtZSI6ImJvYiIsInJvbGUiOiJST0xFX1VTRVIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MTcwMDA4NjQwMH0.signature
```

---

## Error Scenarios

| Scenario | HTTP Status | gRPC Status | Message |
|----------|-------------|-------------|---------|
| Username vazio | 400 | N/A | "Username is required" |
| Password vazio | 400 | N/A | "Password is required" |
| Credenciais inválidas | 401 | N/A | "Invalid username or password" |
| Token ausente | N/A | UNAUTHENTICATED | "Authorization header missing" |
| Token expirado | N/A | UNAUTHENTICATED | "Token expired" |
| Token inválido | N/A | UNAUTHENTICATED | "Invalid token signature" |
| Sender_id mismatch | N/A | PERMISSION_DENIED | "Cannot send message as another user" |

---

## Migration Path

### Phase 0: POC (Current)
- Hardcoded users em memória
- Plain text passwords
- Static JWT secret

### Phase 1: Production Security
- MongoDB UserRepository
- BCrypt password hashing
- Environment variable JWT secret
- Rate limiting no /login

### Phase 2: Advanced Features
- Refresh tokens
- OAuth2 integration (Google, GitHub)
- Role-based authorization (@PreAuthorize)
- Token blacklist (logout funcional)

### Phase 3: Enterprise
- Multi-tenancy support
- SSO (SAML, OIDC)
- Audit logging
- Password reset flow
