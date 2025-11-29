package com.chat.config;

import com.chat.kafka.serialization.ProtobufDeserializer;
import com.chat.kafka.v1.MessageEvent;
import com.chat.kafka.v1.PlatformMessageEvent;
import com.chat.kafka.v1.StateUpdateEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer Configuration
 * 
 * Responsibility: Configures Kafka consumers with manual offset commits for at-least-once delivery.
 * Does NOT: Handle message processing logic (see MessageDeliveryWorker), manage producer configuration (see KafkaProducerConfig).
 * 
 * Distributed Systems Concept: Manual offset commits enable at-least-once delivery semantics.
 * Consumer commits offset ONLY after successful MongoDB persistence, ensuring no message loss
 * even during crashes (research.md Decision 2). Trade-off: Potential duplicate processing requires
 * idempotency handling via message_id unique index (FR-006).
 * 
 * Architecture Decision (Nov 2025): Migrated from JSON to Protocol Buffers for Kafka deserialization.
 * - Type-specific consumer factories use Protobuf parsers (MessageEvent.parser(), StateUpdateEvent.parser())
 * - Schema validation happens during deserialization (fail-fast on incompatible messages)
 * - Performance: 2-3x faster deserialization vs JSON (1-2ms vs 3-5ms)
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * Base consumer configuration shared by all consumer factories.
     * 
     * @return Map of common Kafka consumer properties
     */
    private Map<String, Object> baseConsumerConfig() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Bootstrap servers
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Consumer group ID for load distribution
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        // Deserializers: String for key
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        
        // Start from earliest offset if no committed offset exists
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // FR-026: Disable auto-commit to enable manual offset commits (at-least-once delivery)
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Prefetch limit: max 10 messages per poll (backpressure control)
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        
        return configProps;
    }

    /**
     * Consumer factory for MessageEvent (message-events topic).
     * Uses Protobuf parser for type-safe deserialization.
     * 
     * @return ConsumerFactory configured for MessageEvent Protobuf messages
     */
    @Bean
    public ConsumerFactory<String, MessageEvent> messageEventConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
            baseConsumerConfig(),
            new StringDeserializer(),
            new ProtobufDeserializer<>(MessageEvent.parser())
        );
    }
    
    /**
     * Consumer factory for StateUpdateEvent (state-update-events topic).
     * Uses Protobuf parser for type-safe deserialization.
     * 
     * @return ConsumerFactory configured for StateUpdateEvent Protobuf messages
     */
    @Bean
    public ConsumerFactory<String, StateUpdateEvent> stateUpdateEventConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
            baseConsumerConfig(),
            new StringDeserializer(),
            new ProtobufDeserializer<>(StateUpdateEvent.parser())
        );
    }
    
    /**
     * Consumer factory for PlatformMessageEvent (platform-specific topics).
     * Uses Protobuf parser for type-safe deserialization.
     * 
     * @return ConsumerFactory configured for PlatformMessageEvent Protobuf messages
     */
    @Bean
    public ConsumerFactory<String, PlatformMessageEvent> platformMessageEventConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
            baseConsumerConfig(),
            new StringDeserializer(),
            new ProtobufDeserializer<>(PlatformMessageEvent.parser())
        );
    }

    /**
     * Kafka listener container factory for MessageEvent with manual acknowledgment mode.
     * 
     * @return ConcurrentKafkaListenerContainerFactory for message-events topic
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MessageEvent> messageEventKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, MessageEvent> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(messageEventConsumerFactory());
        
        // MANUAL acknowledgment mode: Consumer must explicitly commit offset after processing
        // This enables at-least-once delivery - offset committed only after MongoDB persistence
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Concurrency: Number of consumer threads (scale horizontally)
        factory.setConcurrency(3);
        
        return factory;
    }
    
    /**
     * Kafka listener container factory for StateUpdateEvent with manual acknowledgment mode.
     * 
     * @return ConcurrentKafkaListenerContainerFactory for state-update-events topic
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, StateUpdateEvent> stateUpdateEventKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, StateUpdateEvent> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(stateUpdateEventConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setConcurrency(3);
        
        return factory;
    }
    
    /**
     * Kafka listener container factory for PlatformMessageEvent with manual acknowledgment mode.
     * 
     * @return ConcurrentKafkaListenerContainerFactory for platform-specific topics
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PlatformMessageEvent> platformMessageEventKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PlatformMessageEvent> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(platformMessageEventConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setConcurrency(3);
        
        return factory;
    }
}
