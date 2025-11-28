package com.chat.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Configuração de métricas customizadas para monitoramento via Prometheus.
 * 
 * Métricas implementadas:
 * - messages_sent_total: Total de mensagens enviadas (Counter)
 * - messages_processed_total: Total de mensagens processadas por status (Counter com tags)
 * - message_latency_seconds: Latência do processamento de mensagens (Timer)
 * - kafka_consumer_lag: Lag do consumidor Kafka (Gauge)
 * - file_upload_size_bytes: Tamanho de arquivos enviados (Histogram)
 * - platform_delivery_latency_ms: Latência de entrega por plataforma (Timer com tags)
 * 
 * Conforme especificado em PLANO-IMPLEMENTACAO-SEMANAS-5-8.md Fase 1.1
 */
@Configuration
public class MetricsConfig {

    // Gauges para armazenar valores atuais
    private final AtomicLong kafkaConsumerLag = new AtomicLong(0);
    private final AtomicLong mongoConnectionPoolUsage = new AtomicLong(0);

    /**
     * Configuração de tags comuns para todas as métricas
     */
    @Bean
    public MeterBinder commonTagsCustomizer(MeterRegistry registry) {
        return (meterRegistry) -> {
            meterRegistry.config().commonTags(
                "application", "chat-api",
                "environment", "production"
            );
        };
    }

    /**
     * Contador de mensagens enviadas
     * Nome: messages_sent_total
     * Tipo: Counter
     * Tags: type (text, file)
     */
    @Bean
    public Counter messagesSentCounter(MeterRegistry registry) {
        return Counter.builder("messages_sent_total")
            .description("Total de mensagens enviadas através do sistema")
            .tag("type", "text")
            .register(registry);
    }

    /**
     * Contador de mensagens processadas por status
     * Nome: messages_processed_total
     * Tipo: Counter
     * Tags: status (SENT, DELIVERED, READ)
     */
    @Bean
    public Counter messagesProcessedCounter(MeterRegistry registry) {
        return Counter.builder("messages_processed_total")
            .description("Total de mensagens processadas por status")
            .tag("status", "unknown")
            .register(registry);
    }

    /**
     * Timer para latência de processamento de mensagens
     * Nome: message_latency_seconds
     * Tipo: Timer (Histogram)
     * Percentis: 50, 95, 99
     */
    @Bean
    public Timer messageLatencyTimer(MeterRegistry registry) {
        return Timer.builder("message_latency_seconds")
            .description("Latência do processamento de mensagens")
            .publishPercentiles(0.50, 0.95, 0.99)
            .register(registry);
    }

    /**
     * Gauge para lag do consumidor Kafka
     * Nome: kafka_consumer_lag
     * Tipo: Gauge
     * Tags: topic, consumer_group
     */
    @Bean
    public Gauge kafkaConsumerLagGauge(MeterRegistry registry) {
        return Gauge.builder("kafka_consumer_lag", kafkaConsumerLag, AtomicLong::get)
            .description("Lag do consumidor Kafka (mensagens pendentes)")
            .tag("topic", "message-events")
            .tag("consumer_group", "chat-api-consumer")
            .register(registry);
    }

    /**
     * Gauge para uso do connection pool do MongoDB
     * Nome: mongodb_connection_pool_usage
     * Tipo: Gauge
     */
    @Bean
    public Gauge mongoConnectionPoolUsageGauge(MeterRegistry registry) {
        return Gauge.builder("mongodb_connection_pool_usage", mongoConnectionPoolUsage, AtomicLong::get)
            .description("Uso atual do connection pool do MongoDB (porcentagem)")
            .register(registry);
    }

    /**
     * Timer para latência de upload de arquivo
     * Nome: file_upload_latency_seconds
     * Tipo: Timer
     * Percentis: 50, 95, 99
     */
    @Bean
    public Timer fileUploadLatencyTimer(MeterRegistry registry) {
        return Timer.builder("file_upload_latency_seconds")
            .description("Latência de upload de arquivos para MinIO")
            .publishPercentiles(0.50, 0.95, 0.99)
            .register(registry);
    }

    /**
     * Timer para latência de entrega por plataforma
     * Nome: platform_delivery_latency_ms
     * Tipo: Timer
     * Tags: platform (whatsapp, instagram)
     * Percentis: 50, 95, 99
     */
    @Bean
    public Timer platformDeliveryLatencyTimer(MeterRegistry registry) {
        return Timer.builder("platform_delivery_latency_ms")
            .description("Latência de entrega por plataforma")
            .publishPercentiles(0.50, 0.95, 0.99)
            .tag("platform", "unknown")
            .register(registry);
    }

    /**
     * Contador de erros por tipo
     * Nome: errors_total
     * Tipo: Counter
     * Tags: error_type, endpoint
     */
    @Bean
    public Counter errorsCounter(MeterRegistry registry) {
        return Counter.builder("errors_total")
            .description("Total de erros por tipo")
            .tag("error_type", "unknown")
            .tag("endpoint", "unknown")
            .register(registry);
    }

    // Métodos auxiliares para atualizar Gauges
    
    public void updateKafkaConsumerLag(long lag) {
        this.kafkaConsumerLag.set(lag);
    }

    public void updateMongoConnectionPoolUsage(long usage) {
        this.mongoConnectionPoolUsage.set(usage);
    }
}
