package com.chat.config;

import com.chat.kafka.serialization.ProtobufSerializer;
import com.chat.kafka.v1.MessageEvent;
import com.chat.kafka.v1.PlatformMessageEvent;
import com.chat.kafka.v1.StateUpdateEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer Configuration
 * 
 * Responsibility: Configures Kafka producer for publishing message events with Protobuf serialization.
 * Does NOT: Handle message routing logic (see MessageService), manage consumer configuration (see KafkaConsumerConfig).
 * 
 * Distributed Systems Concept: Producer with acks=all ensures message replication to all in-sync replicas
 * before acknowledgment, providing durability guarantees. This prevents message loss during broker failures
 * but increases latency slightly (~5-10ms). Acceptable trade-off for at-least-once delivery (research.md Decision 2).
 * 
 * Architecture Decision (Nov 2025): Migrated from JSON to Protocol Buffers for Kafka serialization.
 * - Payload size: 60% reduction (250 bytes JSON → 100 bytes Protobuf)
 * - Serialization: 2-3x faster (3-5ms JSON → 1-2ms Protobuf)
 * - Type safety: Compile-time validation (Protobuf) vs runtime errors (JSON)
 * - Consistency: gRPC + Kafka both use Protobuf (single serialization stack)
 * Trade-off: Debugging more difficult (binary vs text), but performance/consistency gains justify it.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Configures Kafka producer factory with acks=all for durability.
     * 
     * @return ProducerFactory configured for Protobuf serialization with acks=all
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Bootstrap servers (Kafka broker addresses)
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Serializers: String for key (conversation_id as partition key), Protobuf for value
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ProtobufSerializer.class);
        
        // FR-029: acks=all ensures message replicated to all in-sync replicas before ack
        // This provides durability guarantee - message survives broker failures
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        
        // Retry configuration: 3 attempts with exponential backoff
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
        
        // Idempotence: Prevents duplicate messages during retries
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * KafkaTemplate for message publishing operations.
     * 
     * @return KafkaTemplate configured with Protobuf serialization
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
    
    /**
     * KafkaTemplate specifically for MessageEvent messages.
     * 
     * @return KafkaTemplate for message-events topic (Protobuf serialization)
     */
    @Bean
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, MessageEvent> messageEventKafkaTemplate() {
        return new KafkaTemplate<>((ProducerFactory<String, MessageEvent>) (ProducerFactory<?, ?>) producerFactory());
    }
    
    /**
     * KafkaTemplate specifically for StateUpdateEvent messages.
     * 
     * @return KafkaTemplate for state-update-events topic (Protobuf serialization)
     */
    @Bean
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, StateUpdateEvent> stateUpdateEventKafkaTemplate() {
        return new KafkaTemplate<>((ProducerFactory<String, StateUpdateEvent>) (ProducerFactory<?, ?>) producerFactory());
    }
    
    /**
     * KafkaTemplate specifically for PlatformMessageEvent messages.
     * Layer 2 Enhancement: Used for routing messages to platform-specific topics.
     * 
     * @return KafkaTemplate for platform message routing (whatsapp-messages, instagram-messages topics, Protobuf serialization)
     */
    @Bean
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, PlatformMessageEvent> platformKafkaTemplate() {
        return new KafkaTemplate<>((ProducerFactory<String, PlatformMessageEvent>) (ProducerFactory<?, ?>) producerFactory());
    }
}
