package com.chat.kafka.serialization;

import com.google.protobuf.MessageLite;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Generic Protobuf Serializer for Kafka
 * 
 * Responsibility: Converts Protobuf messages to byte arrays for Kafka topics.
 * Does NOT: Require Schema Registry (self-contained serialization using Protobuf's toByteArray()).
 * 
 * Distributed Systems Concept: Binary serialization reduces payload size (60% smaller than JSON)
 * and CPU overhead (2-3x faster). Trade-off: Debugging is harder (binary vs text), but performance
 * gains justify it for production systems.
 * 
 * Usage:
 * - KafkaProducerConfig: VALUE_SERIALIZER_CLASS_CONFIG = ProtobufSerializer.class
 * - Supports any Protobuf generated class (MessageEvent, StateUpdateEvent, PlatformMessageEvent)
 * 
 * @param <T> Protobuf message type (must extend MessageLite)
 */
public class ProtobufSerializer<T extends MessageLite> implements Serializer<T> {
    
    /**
     * Serialize Protobuf message to byte array.
     * 
     * Protobuf's toByteArray() performs zero-copy serialization (no intermediate String allocation).
     * This is significantly faster than Jackson JSON serialization which creates String objects.
     * 
     * @param topic Kafka topic name (unused, required by Serializer interface)
     * @param data Protobuf message to serialize
     * @return Byte array representation, or null if data is null
     */
    @Override
    public byte[] serialize(String topic, T data) {
        if (data == null) {
            return null;
        }
        
        // Protobuf native serialization: Java object → bytes
        // Example sizes (SendMessage):
        // - JSON: ~250 bytes (text-based, human-readable)
        // - Protobuf: ~100 bytes (binary, compact)
        return data.toByteArray();
    }
    
    /**
     * No cleanup needed for Protobuf serialization (stateless).
     */
    @Override
    public void close() {
        // No resources to release
    }
}
