# 03 - Autenticação e Segurança

**Versão**: 1.0  
**Última Atualização**: 29/11/2025  
**Status**: ✅ Implementado (POC - usuários hardcoded)

---

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Arquitetura de Autenticação](#arquitetura-de-autenticação)
3. [JWT Service](#jwt-service)
4. [Endpoint de Login](#endpoint-de-login)
5. [Segurança HTTP](#segurança-http)
6. [Validação gRPC](#validação-grpc)
7. [Decisões e Trade-offs](#decisões-e-trade-offs)

---

## Visão Geral

### Objetivo

Implementar **autenticação stateless** via JWT (JSON Web Tokens) que permita:
- Login via REST API (HTTP POST)
- Validação de token em requisições gRPC
- Escalabilidade horizontal (sem sessões em memória)
- Expiração automática de tokens (24 horas)

### Fluxo de Autenticação

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. Cliente → POST /api/auth/login                                │
│    Body: {"username": "alice", "password": "password123"}        │
└─────────────────────┬────────────────────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────────────────────┐
│ 2. AuthenticationController                                      │
│    - Valida credenciais (UserService)                            │
│    - Gera JWT token (JwtService)                                 │
│    - Retorna token + userInfo                                    │
└─────────────────────┬────────────────────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────────────────────┐
│ 3. Cliente armazena token                                        │
│    Response: {                                                   │
│      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",        │
│      "expiresIn": 86400000,                                      │
│      "user": {"userId": "...", "username": "alice"}              │
│    }                                                             │
└─────────────────────┬────────────────────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────────────────────┐
│ 4. Cliente → gRPC SendMessage                                    │
│    Metadata: "authorization: Bearer <token>"                     │
└─────────────────────┬────────────────────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────────────────────┐
│ 5. gRPC Interceptor (futuro)                                     │
│    - Extrai token do metadata                                    │
│    - Valida assinatura e expiração (JwtService)                  │
│    - Injeta userId no contexto                                   │
│    - Permite ou nega request                                     │
└──────────────────────────────────────────────────────────────────┘
```

---

## Arquitetura de Autenticação

### Componentes

| Componente | Responsabilidade | Tecnologia |
|------------|------------------|------------|
| **AuthenticationController** | Endpoint REST de login | Spring Web |
| **JwtService** | Geração e validação de tokens | JJWT 0.12.3 |
| **UserService** | Validação de credenciais | In-Memory HashMap (POC) |
| **SecurityConfig** | Configuração Spring Security | Spring Security 6.2.5 |
| **gRPC Interceptor** (futuro) | Validação de token em gRPC | gRPC Interceptor API |

### Portas

- **HTTP/REST**: `8081` - Login, health check
- **gRPC**: `9090` - APIs protegidas (futuro: requer JWT em metadata)

---

## JWT Service

### Localização

**Arquivo**: `src/main/java/com/chat/service/JwtService.java`

### Configuração

**Arquivo**: `application.yml`
```yaml
jwt:
  secret: minha-chave-super-secreta-deve-ter-pelo-menos-256-bits  # 32+ chars
  expiration: 86400000  # 24 horas em milissegundos
```

### Implementação

#### 1. Geração de Token

```java
@Service
public class JwtService {
    
    @Value("${jwt.secret}")
    private String secretKey;
    
    @Value("${jwt.expiration}")
    private Long expirationTime;
    
    public String generateToken(String userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("role", role);
        
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);
        
        // Constrói JWT: Header + Payload + Signature
        String token = Jwts.builder()
                .setClaims(claims)               // Custom claims
                .setSubject(userId)              // "sub" = user ID
                .setIssuedAt(now)                // "iat" = timestamp
                .setExpiration(expiryDate)       // "exp" = 24h depois
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)  // HMAC-SHA256
                .compact();
        
        return token;
    }
    
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);  // Converte string para SecretKey
    }
}
```

**Estrutura do Token JWT**:

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6ImFsaWNlIiwicm9sZSI6IlJPTEVfVVNFUiIsInN1YiI6ImExYTFhMWExLTExMTEtMTExMS0xMTExLTExMTExMTExMTExMSIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoxNzAwMDg2NDAwfQ.signature
```

**Decodificado**:

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
  "username": "alice",
  "role": "ROLE_USER",
  "sub": "a1a1a1a1-1111-1111-1111-111111111111",
  "iat": 1700000000,
  "exp": 1700086400
}
```

**Signature**:
```
HMACSHA256(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  secret
)
```

#### 2. Validação de Token

```java
public boolean validateToken(String token) {
    try {
        Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);  // Valida signature + expiration
        return true;
    } catch (io.jsonwebtoken.ExpiredJwtException e) {
        logger.warn("JWT token expired: {}", e.getMessage());
        return false;
    } catch (io.jsonwebtoken.security.SignatureException e) {
        logger.warn("Invalid JWT signature: {}", e.getMessage());
        return false;
    } catch (Exception e) {
        logger.warn("JWT validation failed: {}", e.getMessage());
        return false;
    }
}
```

**Validações Realizadas**:
1. **Signature**: Verifica se token foi assinado com secret correto (previne tampering)
2. **Expiration**: Checa se timestamp atual < `exp` claim
3. **Format**: Valida estrutura Base64Url de 3 partes

#### 3. Extração de Claims

```java
public String extractUserId(String token) {
    Claims claims = extractAllClaims(token);
    return claims.getSubject();  // "sub" claim = userId
}

public String extractUsername(String token) {
    Claims claims = extractAllClaims(token);
    return claims.get("username", String.class);
}

public String extractRole(String token) {
    Claims claims = extractAllClaims(token);
    return claims.get("role", String.class);
}

private Claims extractAllClaims(String token) {
    return Jwts.parser()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .getBody();
}
```

---

## Endpoint de Login

### Localização

**Arquivo**: `src/main/java/com/chat/controller/AuthenticationController.java`

### Request/Response

**Endpoint**: `POST /api/auth/login`

**Request Body**:
```json
{
  "username": "alice",
  "password": "password123"
}
```

**Success Response (200 OK)**:
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

**Error Response (401 Unauthorized)**:
```json
{
  "error": "Unauthorized",
  "message": "Invalid username or password",
  "timestamp": "2025-11-29T10:30:00.000Z"
}
```

### Implementação

```java
@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private JwtService jwtService;
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        logger.info("Login attempt for username: {}", request.getUsername());
        
        // 1. Valida credenciais
        if (!userService.validateCredentials(request.getUsername(), request.getPassword())) {
            logger.warn("Failed login attempt for username: {}", request.getUsername());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Unauthorized");
            errorResponse.put("message", "Invalid username or password");
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
        
        // 2. Busca usuário
        AuthUser user = userService.findByUsername(request.getUsername()).get();
        
        // 3. Gera JWT token
        String token = jwtService.generateToken(user.getUserId(), 
                                                user.getUsername(), 
                                                user.getRole());
        
        // 4. Monta response
        LoginResponse response = LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .user(UserInfo.builder()
                        .userId(user.getUserId())
                        .username(user.getUsername())
                        .role(user.getRole())
                        .build())
                .build();
        
        logger.info("Login successful for user: {}", user.getUsername());
        
        return ResponseEntity.ok(response);
    }
}
```

### UserService (POC - Hardcoded)

**Arquivo**: `src/main/java/com/chat/service/UserService.java`

```java
@Service
public class UserService {
    
    // POC: Usuários hardcoded em memória
    private final Map<String, AuthUser> users = Map.of(
        "alice", new AuthUser(
            "a1a1a1a1-1111-1111-1111-111111111111", 
            "alice", 
            "password123",  // Plaintext (POC only!)
            "ROLE_USER"
        ),
        "bob", new AuthUser(
            "b2b2b2b2-2222-2222-2222-222222222222", 
            "bob", 
            "password123", 
            "ROLE_USER"
        ),
        "admin", new AuthUser(
            "00000000-0000-0000-0000-000000000000", 
            "admin", 
            "admin123", 
            "ROLE_ADMIN"
        )
    );
    
    public boolean validateCredentials(String username, String password) {
        AuthUser user = users.get(username);
        if (user == null) {
            return false;
        }
        
        // POC: Plaintext comparison
        // Production: BCrypt.checkpw(password, user.getPasswordHash())
        return user.getPassword().equals(password);
    }
    
    public Optional<AuthUser> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }
}
```

**⚠️ Limitações POC**:
- Senhas em plaintext (produção requer BCrypt)
- Usuários hardcoded (produção requer MongoDB `users` collection)
- Sem rate limiting (produção requer proteção contra brute force)

---

## Segurança HTTP

### Configuração

**Arquivo**: `src/main/java/com/chat/config/SecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Desabilita CSRF (stateless JWT authentication)
            .csrf(csrf -> csrf.disable())
            
            // Regras de autorização
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()     // Público
                .requestMatchers("/api/auth/health").permitAll()    // Público
                .requestMatchers("/actuator/**").permitAll()        // Métricas
                .anyRequest().permitAll()  // POC: permite tudo (mudar para .authenticated())
            );
        
        return http.build();
    }
}
```

### Por Que CSRF Desabilitado?

**Motivo**: JWT-based stateless authentication não usa cookies

**Explicação**:
- CSRF protege contra forged requests usando cookies de sessão
- JWT é enviado no header `Authorization: Bearer <token>`
- Atacante não consegue ler header via JavaScript cross-origin (CORS)
- Logo, CSRF não é vetor de ataque em JWT stateless

**Trade-off**: Se futuro adicionar cookies, REABILITAR CSRF!

---

## Validação gRPC

### Status Atual

⚠️ **NÃO IMPLEMENTADO** (futuro)

### Implementação Planejada

**Arquivo**: `src/main/java/com/chat/grpc/AuthenticationInterceptor.java` (criar)

```java
@Component
public class AuthenticationInterceptor implements ServerInterceptor {
    
    @Autowired
    private JwtService jwtService;
    
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        
        // Extrai token do metadata
        String authHeader = headers.get(
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER));
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.close(Status.UNAUTHENTICATED
                    .withDescription("Missing or invalid authorization header"), 
                    new Metadata());
            return new ServerCall.Listener<>() {};
        }
        
        String token = authHeader.substring(7);  // Remove "Bearer "
        
        // Valida token
        if (!jwtService.validateToken(token)) {
            call.close(Status.UNAUTHENTICATED
                    .withDescription("Invalid or expired token"), 
                    new Metadata());
            return new ServerCall.Listener<>() {};
        }
        
        // Extrai userId e injeta no contexto
        String userId = jwtService.extractUserId(token);
        Context context = Context.current()
                .withValue(USER_ID_CONTEXT_KEY, userId);
        
        // Continua com contexto autenticado
        return Contexts.interceptCall(context, call, headers, next);
    }
}
```

**Uso nos Services**:

```java
@GRpcService
public class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {
    
    @Override
    public void sendMessage(SendMessageRequest request, ...) {
        // Extrai userId do contexto (injetado pelo interceptor)
        String authenticatedUserId = USER_ID_CONTEXT_KEY.get();
        
        // Valida que sender_id == authenticatedUserId
        if (!request.getSenderId().equals(authenticatedUserId)) {
            responseObserver.onError(Status.PERMISSION_DENIED
                    .withDescription("sender_id must match authenticated user")
                    .asRuntimeException());
            return;
        }
        
        // Processa mensagem...
    }
}
```

---

## Decisões e Trade-offs

### Decision 1: JWT vs Session-Based Auth

| Aspecto | JWT (Escolhido) | Session-Based |
|---------|-----------------|---------------|
| **Stateless** | ✅ Sim | ❌ Não (Redis required) |
| **Horizontal Scale** | ✅ Nenhuma coordenação | ⚠️ Shared session store |
| **Revocation** | ❌ Difícil (need blacklist) | ✅ Fácil (delete session) |
| **Token Size** | ⚠️ ~200 bytes | ✅ Session ID pequeno |
| **Expiration** | ✅ Built-in | ⚠️ Manual |

**Decisão**: JWT para demonstrar stateless scaling (educacional)

**Referência**: `authentication-jwt-research.md` - Decision 3

### Decision 2: HS256 vs RS256

| Aspecto | HS256 (Escolhido) | RS256 |
|---------|-------------------|-------|
| **Setup** | ✅ 1 string secret | ❌ Certificate management |
| **Performance** | ✅ Mais rápido | ⚠️ Assimetric slower |
| **Key Rotation** | ⚠️ Manual | ✅ Separar public/private |
| **Microservices** | ⚠️ Secret sharing | ✅ Public key distribution |

**Decisão**: HS256 para MVP (simples, rápido, educacional)

**Produção**: Migrar para RS256 se múltiplos microserviços

**Referência**: `authentication-jwt-research.md` - Decision 2

### Decision 3: REST Login vs gRPC Login

| Aspecto | REST (Escolhido) | gRPC |
|---------|------------------|------|
| **Padrão Indústria** | ✅ OAuth2 usa HTTP | ❌ Não comum |
| **Postman Support** | ✅ Nativo | ⚠️ Plugins |
| **Debug** | ✅ curl friendly | ❌ grpcurl complexo |
| **Performance** | ⚠️ JSON overhead | ✅ Protobuf |

**Decisão**: REST para login (compatibilidade, ergonomia)

**Referência**: `authentication-jwt-research.md` - Decision 3

### Decision 4: Hardcoded Users vs MongoDB

**Escolhido**: Hardcoded (POC)

**Rationale**:
- POC-first: Valida fluxo JWT SEM adicionar dependência MongoDB
- Reversível: Fácil migrar depois
- Testável: Usuários previsíveis
- Educacional: Foco em JWT, não user management

**Migration Path**:
```java
// 1. Criar collection
@Document(collection = "users")
public class User {
    @Id private ObjectId id;
    private String userId;
    private String username;
    private String passwordHash;  // BCrypt
    private String role;
}

// 2. Repository
public interface UserRepository extends MongoRepository<User, ObjectId> {
    Optional<User> findByUsername(String username);
}

// 3. Service
public boolean validateCredentials(String username, String password) {
    User user = userRepository.findByUsername(username).orElse(null);
    if (user == null) return false;
    
    return BCrypt.checkpw(password, user.getPasswordHash());
}
```

**Referência**: `authentication-jwt-research.md` - Decision 4

---

## Testes

### Teste Manual (cURL)

```bash
# Login
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123"}'

# Response
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

# Usar token em gRPC (futuro)
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -d '{"message_id":"...","conversation_id":"...","message_text":"Hello"}' \
  localhost:9090 \
  chat_api.v1.ChatService/SendMessage
```

### Usuários Disponíveis (POC)

| Username | Password | Role | User ID |
|----------|----------|------|---------|
| `alice` | `password123` | `ROLE_USER` | `a1a1a1a1-1111-1111-1111-111111111111` |
| `bob` | `password123` | `ROLE_USER` | `b2b2b2b2-2222-2222-2222-222222222222` |
| `admin` | `admin123` | `ROLE_ADMIN` | `00000000-0000-0000-0000-000000000000` |

---

## Segurança - Próximos Passos (Produção)

### 1. Password Hashing

```java
// Adicionar BCrypt
String passwordHash = BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
boolean valid = BCrypt.checkpw(plainPassword, passwordHash);
```

### 2. Rate Limiting

```java
// Limitar tentativas de login (Bucket4j ou Resilience4j)
@RateLimiter(name = "login", fallbackMethod = "loginRateLimitFallback")
public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
    // ...
}
```

### 3. Secret em Variável de Ambiente

```yaml
# application-production.yml
jwt:
  secret: ${JWT_SECRET}  # Load from env var
  expiration: 3600000    # 1 hora (mais curto em produção)
```

### 4. Token Refresh

```java
POST /api/auth/refresh
Body: {"refreshToken": "..."}
Response: {"token": "...", "expiresIn": 3600000}
```

### 5. gRPC Interceptor

Implementar `AuthenticationInterceptor` conforme seção "Validação gRPC"

---

## Referências

- **Decisões**: `specs/001-ubiquitous-messaging-platform/authentication-jwt-research.md`
- **Data Model**: `specs/001-ubiquitous-messaging-platform/authentication-jwt-data-model.md`
- **Quickstart**: `specs/001-ubiquitous-messaging-platform/authentication-jwt-quickstart.md`
- **Código**:
  - `src/main/java/com/chat/service/JwtService.java`
  - `src/main/java/com/chat/controller/AuthenticationController.java`
  - `src/main/java/com/chat/config/SecurityConfig.java`

---

**Próximo Documento**: [04-KAFKA-E-PROCESSAMENTO-ASSINCRONO.md](04-KAFKA-E-PROCESSAMENTO-ASSINCRONO.md)
