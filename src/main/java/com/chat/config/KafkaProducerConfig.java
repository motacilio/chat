package com.chat.config;

import com.chat.dto.MessageEventDto;
import com.chat.dto.PlatformMessageEventDto;
import com.chat.dto.StateUpdateEventDto;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer Configuration
 * 
 * Responsibility: Configures Kafka producer for publishing message events with JSON serialization.
 * Does NOT: Handle message routing logic (see MessageService), manage consumer configuration (see KafkaConsumerConfig).
 * 
 * Distributed Systems Concept: Producer with acks=all ensures message replication to all in-sync replicas
 * before acknowledgment, providing durability guarantees. This prevents message loss during broker failures
 * but increases latency slightly (~5-10ms). Acceptable trade-off for at-least-once delivery (research.md Decision 2).
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Configures Kafka producer factory with acks=all for durability.
     * 
     * @return ProducerFactory configured for JSON serialization with acks=all
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Bootstrap servers (Kafka broker addresses)
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Serializers: String for key (conversation_id as partition key), JSON for value
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
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
     * @return KafkaTemplate configured with JSON serialization
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
    
    /**
     * KafkaTemplate specifically for MessageEventDto messages.
     * 
     * @return KafkaTemplate for message-events topic
     */
    @Bean
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, MessageEventDto> messageEventKafkaTemplate() {
        return new KafkaTemplate<>((ProducerFactory<String, MessageEventDto>) (ProducerFactory<?, ?>) producerFactory());
    }
    
    /**
     * KafkaTemplate specifically for StateUpdateEventDto messages.
     * 
     * @return KafkaTemplate for state-update-events topic
     */
    @Bean
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, StateUpdateEventDto> stateUpdateEventKafkaTemplate() {
        return new KafkaTemplate<>((ProducerFactory<String, StateUpdateEventDto>) (ProducerFactory<?, ?>) producerFactory());
    }
    
    /**
     * KafkaTemplate specifically for PlatformMessageEventDto messages.
     * Layer 2 Enhancement: Used for routing messages to platform-specific topics.
     * 
     * @return KafkaTemplate for platform message routing (whatsapp-messages, instagram-messages topics)
     */
    @Bean
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, PlatformMessageEventDto> platformKafkaTemplate() {
        return new KafkaTemplate<>((ProducerFactory<String, PlatformMessageEventDto>) (ProducerFactory<?, ?>) producerFactory());
    }
}
