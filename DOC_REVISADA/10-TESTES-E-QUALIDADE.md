# 10 - Testes e Qualidade

**Versão**: 1.0  
**Status**: ✅ Implementado

---

## Estratégia de Testes

| Tipo | Ferramenta | Cobertura | Localização |
|------|------------|-----------|-------------|
| **Unit Tests** | JUnit 5 + Mockito | Service layer | `src/test/java/` |
| **Integration Tests** | Spring Boot Test | Controllers, Repositories | `src/test/java/` |
| **Load Tests** | k6 (HTTP), ghz (gRPC) | Performance | `scripts/load-test/` |
| **API Tests** | Postman Collections | E2E flows | `tools/` |

---

## Unit Tests

**Exemplo**: `WebhookControllerTest.java`

```java
@SpringBootTest
@AutoConfigureMockMvc
class WebhookControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private ConversationService conversationService;
    
    @Test
    void testWhatsAppWebhook_Success() throws Exception {
        String payload = "{\"from\":\"+5511987654321\",\"text\":\"Hello\"}";
        
        mockMvc.perform(post("/api/webhooks/whatsapp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());
        
        verify(conversationService, times(1)).processIncomingMessage(any());
    }
}
```

**Comando**:
```powershell
mvn test
```

---

## Load Tests

### k6 (HTTP/REST)

**Arquivo**: `scripts/load-test/k6-send-messages.js`

```javascript
import http from 'k6/http';

export let options = {
  vus: 100,        // 100 virtual users
  duration: '30s',
};

export default function() {
  const payload = JSON.stringify({
    message_id: uuidv4(),
    conversation_id: 'conv-123',
    sender_id: 'user-456',
    message_text: 'Load test message'
  });
  
  http.post('http://localhost:8081/api/messages', payload, {
    headers: { 'Content-Type': 'application/json' },
  });
}
```

**Executar**:
```powershell
k6 run scripts/load-test/k6-send-messages.js
```

### ghz (gRPC)

**Arquivo**: `scripts/load-test/ghz-send-message-data.json`

```json
{
  "message_id": "{{randomUUID}}",
  "conversation_id": "conv-123",
  "sender_id": "user-456",
  "message_text": "gRPC load test"
}
```

**Executar**:
```powershell
ghz --insecure `
    --proto src/main/proto/chat_service.proto `
    --call chat_api.v1.ChatService/SendMessage `
    -d @scripts/load-test/ghz-send-message-data.json `
    -n 10000 -c 100 `
    localhost:9090
```

**Resultados Esperados**:
- **p95 latency**: <100ms
- **Throughput**: 1000+ req/s
- **Error rate**: <1%

---

## Postman Collections

**Arquivo**: `tools/Postman-Layer2-FileUpload-Tests.json`

**Tests Incluídos**:
1. Login (POST /api/auth/login)
2. Create Conversation
3. Send Text Message
4. Upload File
5. Stream Messages (manual test)

**Importar no Postman**:
```
File → Import → tools/Postman-Layer2-FileUpload-Tests.json
```

---

## Cobertura de Testes

**Comando**:
```powershell
mvn test jacoco:report
```

**Relatório**: `target/site/jacoco/index.html`

---

**Próximo**: [11-SCRIPTS-E-DEPLOYMENT.md](11-SCRIPTS-E-DEPLOYMENT.md)
