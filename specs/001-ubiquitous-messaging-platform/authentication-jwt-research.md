# Research: Autenticação JWT Simples

## Decision 1: Biblioteca JWT

**Chosen**: `jjwt` (io.jsonwebtoken:jjwt-api:0.12.3)

**Rationale**:
- Padrão da indústria para Java (usado por Spring Security)
- API simples e type-safe
- Suporta múltiplos algoritmos de assinatura (HS256, RS256)
- Bem documentado e mantido
- Integração nativa com Spring Boot

**Alternatives Rejected**:
- `auth0/java-jwt`: Menos integração com Spring Security
- `nimbus-jose-jwt`: API mais complexa, overkill para uso educacional
- Implementação custom: Viola Princípio VIII (Standard Library Reuse)

**References**:
- https://github.com/jwtk/jjwt
- https://jwt.io/introduction

---

## Decision 2: Estratégia de Chave

**Chosen**: Chave estática simétrica (HS256) com secret configurável

**Rationale**:
- Educacional: Demonstra conceito de assinatura sem complexidade de PKI
- Simples de configurar: Uma única string no `application.properties`
- Suficiente para MVP: Sistema não requer multi-tenancy ou key rotation
- POC-first: Valida fluxo de autenticação antes de adicionar complexidade

**Alternatives Rejected**:
- RS256 (chave assimétrica): Requer gerenciamento de certificados, complexidade desnecessária para MVP
- Key rotation automático: Premature optimization, adicionar em P2 se necessário
- Database-stored keys: Adiciona dependência de DB para autenticação, viola stateless

**Configuration**:
```properties
# application.properties
jwt.secret=minha-chave-super-secreta-deve-ter-pelo-menos-256-bits
jwt.expiration=86400000  # 24 horas em milissegundos
```

---

## Decision 3: Fluxo de Autenticação

**Chosen**: Endpoint de login REST + Interceptor gRPC para validação

**Rationale**:
- Separação de responsabilidades: Login via HTTP (stateless), APIs via gRPC (stateful streams)
- Educacional: Demonstra dois protocolos trabalhando juntos (REST + gRPC)
- Compatibilidade: Postman suporta HTTP melhor que gRPC auth
- Padrão da indústria: OAuth2 usa HTTP para token issuance

**Flow**:
```
1. Client → POST /api/auth/login {username, password}
2. Server valida credenciais (hardcoded por enquanto)
3. Server gera JWT token
4. Client recebe {token, expiresIn}
5. Client inclui token em metadata gRPC: "authorization: Bearer <token>"
6. Interceptor gRPC valida token em cada request
```

**Alternatives Rejected**:
- Login via gRPC: Menos ergonômico para testing, não é padrão da indústria
- Session-based auth: Viola stateless (Princípio V), não escala horizontalmente
- Basic Auth: Menos seguro, não suporta expiration

---

## Decision 4: Armazenamento de Usuários (POC)

**Chosen**: Hardcoded users em memória (HashMap)

**Rationale**:
- POC-first (Princípio VIII): Valida fluxo de autenticação SEM adicionar DB dependency
- Reversível: Fácil migrar para MongoDB depois
- Testável: Users previsíveis facilitam testes automatizados
- Educacional: Foco em JWT, não em user management

**POC Users**:
```java
Map<String, User> users = Map.of(
    "alice", new User("a1a1a1a1-1111-1111-1111-111111111111", "alice", "password123", "ROLE_USER"),
    "bob", new User("b2b2b2b2-2222-2222-2222-222222222222", "bob", "password123", "ROLE_USER"),
    "admin", new User("00000000-0000-0000-0000-000000000000", "admin", "admin123", "ROLE_ADMIN")
);
```

**Migration Path (P2)**:
- Adicionar `users` collection no MongoDB
- Implementar `UserRepository extends MongoRepository`
- Substituir HashMap por repository.findByUsername()
- Adicionar password hashing (BCrypt)

**Alternatives Rejected**:
- MongoDB desde início: Premature - adiciona complexidade antes de validar fluxo
- Environment variables: Não escala para múltiplos users
- Properties file: Dificulta atualização dinâmica

---

## Decision 5: Password Handling (POC)

**Chosen**: Plain text passwords (APENAS para POC)

**Rationale**:
- POC-first: Validar fluxo JWT antes de adicionar hashing
- Transparência educacional: Facilita debugging e testes
- Reversível: Trocar por BCrypt é trivial (1 linha de código)

**⚠️ SECURITY WARNING**: Plain text passwords são INACEITÁVEIS em produção

**Migration Path (antes de produção)**:
```java
// POC (current)
boolean isValid = storedPassword.equals(inputPassword);

// Production (P2)
boolean isValid = BCrypt.checkpw(inputPassword, storedPasswordHash);
```

**Alternatives Rejected**:
- BCrypt desde início: Adiciona dependência, ofusca conceito de JWT
- SHA-256: Inseguro para passwords (não é designed para isso)

---

## Decision 6: Token Validation Strategy

**Chosen**: gRPC ServerInterceptor com whitelist de endpoints públicos

**Rationale**:
- Centralizado: Uma classe valida TODAS as requests gRPC
- Educacional: Demonstra interceptors (aspect-oriented programming)
- Flexível: Fácil adicionar endpoints públicos (ex: health check)
- Padrão: Mesmo pattern usado em Spring Security Filters

**Implementation**:
```java
@Component
public class JwtAuthenticationInterceptor implements ServerInterceptor {
    private static final Set<String> PUBLIC_METHODS = Set.of(
        "chat.ChatService/StreamMessages"  // Exemplo de método público
    );
    
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call,
        Metadata headers,
        ServerCallHandler<ReqT, RespT> next) {
        
        String methodName = call.getMethodDescriptor().getFullMethodName();
        if (PUBLIC_METHODS.contains(methodName)) {
            return next.startCall(call, headers);
        }
        
        String token = extractToken(headers);
        Claims claims = jwtService.validateToken(token);
        
        Context ctx = Context.current().withValue(USER_CONTEXT_KEY, claims.getSubject());
        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
```

**Alternatives Rejected**:
- Validação manual em cada método: Código duplicado, error-prone
- Annotation-based (@Secured): Requer Spring Security full, complexidade excessiva
- Filter-based: Filters são para HTTP, não gRPC

---

## Decision 7: Error Handling

**Chosen**: gRPC Status.UNAUTHENTICATED para falhas de auth

**Rationale**:
- Padrão gRPC: Status codes semânticos (não HTTP 401)
- Client-friendly: Postman mostra erro claro
- Consistente: Mesmo pattern para todas as falhas de auth

**Error Scenarios**:
```java
// Token ausente
throw Status.UNAUTHENTICATED
    .withDescription("Authorization header missing")
    .asRuntimeException();

// Token expirado
throw Status.UNAUTHENTICATED
    .withDescription("Token expired")
    .asRuntimeException();

// Token inválido
throw Status.UNAUTHENTICATED
    .withDescription("Invalid token signature")
    .asRuntimeException();
```

**Alternatives Rejected**:
- Status.PERMISSION_DENIED: Semântica errada (não é autorização, é autenticação)
- Custom error codes: Não é padrão gRPC, confunde clients
- Exception propagation: Não permite error details estruturados

---

## Best Practices (JJWT)

```java
// 1. Sempre validar signature
Jwts.parserBuilder()
    .setSigningKey(key)
    .build()
    .parseClaimsJws(token);

// 2. Sempre checar expiration (default: enabled)
// jjwt faz isso automaticamente, throws ExpiredJwtException

// 3. Sempre usar algoritmo específico (evita "none" attack)
Jwts.builder()
    .signWith(key, SignatureAlgorithm.HS256)  // Explicit algorithm
    .compact();

// 4. Nunca logar tokens (vazamento de credenciais)
log.info("User authenticated: {}", username);  // ✅ OK
log.info("Token: {}", token);  // ❌ NEVER
```

---

## POC Acceptance Criteria

✅ Endpoint POST /api/auth/login aceita {username, password}  
✅ Login bem-sucedido retorna {token, expiresIn}  
✅ Login falho retorna 401 Unauthorized  
✅ Requests gRPC SEM token retornam UNAUTHENTICATED  
✅ Requests gRPC COM token válido são processadas normalmente  
✅ Requests gRPC COM token expirado retornam UNAUTHENTICATED  
✅ Integration test valida fluxo completo: login → get token → call gRPC → success  

---

## Dependencies

```xml
<!-- pom.xml -->
<dependencies>
    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.3</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.3</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.3</version>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Spring Boot Web (para endpoint REST de login) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

---

## Migration to Production Checklist

- [ ] Substituir hardcoded users por MongoDB UserRepository
- [ ] Adicionar BCrypt password hashing
- [ ] Mover jwt.secret para environment variable (não commitar no git)
- [ ] Implementar refresh token (evita re-login frequente)
- [ ] Adicionar rate limiting no endpoint /login (prevenir brute force)
- [ ] Implementar token blacklist para logout
- [ ] Adicionar monitoring de falhas de autenticação (Prometheus counter)
- [ ] Documentar fluxo de autenticação em docs/features/authentication.md
