package com.chat.config;

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
import org.springframework.kafka.support.serializer.JsonDeserializer;

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
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * Configures Kafka consumer factory with manual offset commits.
     * 
     * @return ConsumerFactory configured for JSON deserialization with manual ack mode
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Bootstrap servers
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Consumer group ID for load distribution
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        // Deserializers: String for key, JSON for value
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // Start from earliest offset if no committed offset exists
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // FR-026: Disable auto-commit to enable manual offset commits (at-least-once delivery)
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Prefetch limit: max 10 messages per poll (backpressure control)
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        
        // JSON deserializer: trust all packages (development mode)
        // In production, specify exact packages for security
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        
        return new DefaultKafkaConsumerFactory<>(
            configProps,
            new StringDeserializer(),
            new JsonDeserializer<>(Object.class)
        );
    }

    /**
     * Kafka listener container factory with manual acknowledgment mode.
     * 
     * @return ConcurrentKafkaListenerContainerFactory configured for manual offset commits
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        
        // MANUAL acknowledgment mode: Consumer must explicitly commit offset after processing
        // This enables at-least-once delivery - offset committed only after MongoDB persistence
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Concurrency: Number of consumer threads (scale horizontally)
        factory.setConcurrency(3);
        
        return factory;
    }
}
