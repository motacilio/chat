# 09 - Observabilidade e Monitoramento

**Versão**: 1.0  
**Status**: ✅ Implementado

---

## Stack de Observabilidade

| Componente | Tecnologia | Porta | Propósito |
|------------|------------|-------|-----------|
| **Métricas** | Prometheus | 9090 (scrape 8081/actuator) | Coleta de métricas |
| **Dashboards** | Grafana | 3000 | Visualização |
| **Health Checks** | Actuator | 8081/actuator | Endpoints de saúde |
| **Logs** | Logback + ELK (futuro) | - | Logs estruturados |

---

## Spring Actuator

**Endpoints Disponíveis**:

```
GET http://localhost:8081/actuator/health       - Status UP/DOWN
GET http://localhost:8081/actuator/metrics      - Lista de métricas
GET http://localhost:8081/actuator/prometheus   - Formato Prometheus
GET http://localhost:8081/actuator/info         - App info
```

**Configuração**: `application.yml`
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
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
    scrape_interval: 15s
    static_configs:
      - targets: ['chat-api:8081']
```

**Métricas Coletadas**:
- `http_server_requests_seconds` - Latência HTTP
- `kafka_consumer_records_consumed_total` - Mensagens Kafka consumidas
- `jvm_memory_used_bytes` - Memória JVM
- `system_cpu_usage` - CPU

---

## Grafana

**Dashboard**: `docs/observabilidade/grafana-dashboard-basic.json`

**Painéis**:
1. **Latência p95** - http_server_requests_seconds{quantile="0.95"}
2. **Throughput Kafka** - rate(kafka_consumer_records_consumed_total[1m])
3. **Uso de Memória** - jvm_memory_used_bytes
4. **Taxa de Erro** - http_server_requests_seconds_count{status=~"5.."}

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

**Próximo**: [10-TESTES-E-QUALIDADE.md](10-TESTES-E-QUALIDADE.md)
