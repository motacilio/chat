# 09 - Observabilidade e Monitoramento

**Versão**: 2.0  
**Status**: ✅ Implementado  
**Última atualização**: 01/12/2025

---

## Stack de Observabilidade

| Componente | Tecnologia | Porta | Propósito |
|------------|------------|-------|-----------|
| **Métricas** | Prometheus | 9091 (scrape 8081/actuator) | Coleta de métricas |
| **Dashboards** | Grafana | 3000 | Visualização |
| **Health Checks** | Actuator | 8081/actuator | Endpoints de saúde |
| **Logs** | Logback + ELK (futuro) | - | Logs estruturados |

---

## Spring Actuator

**Endpoints Disponíveis**:

```
GET http://localhost:8081/actuator/health       - Status UP/DOWN
GET http://localhost:8081/actuator/metrics      - Lista de métricas
GET http://localhost:8081/actuator/prometheus   - Formato Prometheus (via workaround)
GET http://localhost:8081/actuator/info         - App info
```

**⚠️ Workaround Spring Boot 3.2.5 Bug**:

O endpoint `/actuator/prometheus` é exposto via `PrometheusController.java` (não auto-configuração):

```java
@RestController
@RequestMapping("/actuator")
public class PrometheusController {
    private final PrometheusMeterRegistry prometheusMeterRegistry;

    @GetMapping(value = "/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
    public String prometheus() {
        return prometheusMeterRegistry.scrape();
    }
}
```

**Motivo**: Bug em `@ConditionalOnAvailableEndpoint` do Spring Boot 3.2.5 impede auto-configuração.

**Configuração**: `application.yml`
```yaml
management:
  server:
    port: 8081
  endpoints:
    enabled-by-default: true
    web:
      exposure:
        include: "*"
  endpoint:
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
```

---

## Prometheus

**Configuração**: `prometheus.yml`
```yaml
scrape_configs:
  - job_name: 'chat-api'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
    static_configs:
      - targets: ['chat-api:8081']
```

**Métricas Coletadas** (~57KB, 1000+ métricas):
- `http_server_requests_seconds` - Latência HTTP
- `kafka_consumer_lag` - Lag do consumidor Kafka
- `resilience4j_circuitbreaker_buffered_calls` - Circuit Breaker status
- `jvm_memory_used_bytes` - Memória JVM
- `system_cpu_usage` - CPU

**Acesso**: http://localhost:9091

---

## Grafana

**Dashboard**: Ver `docs/GUIA-GRAFANA-PROMETHEUS-TESTES.md` para configuração completa

**Painéis Recomendados**:
1. **Latência p95** - `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[1m]))`
2. **Kafka Lag** - `kafka_consumer_lag{consumer_group="chat-api-consumer"}`
3. **Circuit Breaker** - `resilience4j_circuitbreaker_buffered_calls`
4. **Uso de Memória** - `jvm_memory_used_bytes{area="heap"}`
5. **Taxa de Erro** - `rate(http_server_requests_seconds_count{status=~"5.."}[1m])`

**Acesso**: http://localhost:3000 (admin/admin)

---

## Logs Estruturados

**Configuração**: `logback-spring.xml`
```xml
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>trace_id</includeMdcKeyName>
        <includeMdcKeyName>user_id</includeMdcKeyName>
    </encoder>
</appender>
```

**Exemplo de Log**:
```json
{
  "timestamp": "2025-11-29T10:30:00.123Z",
  "level": "INFO",
  "logger": "com.chat.worker.MessageDeliveryWorker",
  "message": "Message processed",
  "trace_id": "abc123",
  "user_id": "user-456",
  "message_id": "msg-789"
}
```

---

## Referências

- **Guia Completo**: `docs/GUIA-GRAFANA-PROMETHEUS-TESTES.md`
  - Configuração end-to-end de Prometheus e Grafana
  - Dashboards prontos com 5 painéis
  - Testes de carga com k6 e ghz
  - Chaos engineering (simulação de falhas MongoDB/Kafka)
  - 50+ exemplos de queries PromQL
  - Troubleshooting completo (incluindo Spring Boot 3.2.5 bug)

- **Workaround Spring Boot 3.2.5**: Ver seção 8.2 do guia completo
- **Dashboard JSON**: `docs/observabilidade/grafana-dashboard-basic.json`

---

**Próximo**: [10-TESTES-E-QUALIDADE.md](10-TESTES-E-QUALIDADE.md)
