package com.chat.kafka.serialization;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * Generic Protobuf Deserializer for Kafka
 * 
 * Responsibility: Converts byte arrays from Kafka topics back to Protobuf messages.
 * Does NOT: Require Schema Registry (uses Protobuf's generated Parser for each message type).
 * 
 * Distributed Systems Concept: Protobuf parsers validate schema during deserialization.
 * If producer sends incompatible message (e.g., missing required field), parser throws
 * InvalidProtocolBufferException. This provides fail-fast behavior compared to JSON
 * which might silently accept invalid data.
 * 
 * Usage:
 * - KafkaConsumerConfig: Create type-specific factories using Parser from generated classes
 * - Example: new ProtobufDeserializer<>(MessageEvent.parser())
 * 
 * @param <T> Protobuf message type (must extend MessageLite)
 */
public class ProtobufDeserializer<T> implements Deserializer<T> {
    
    private final Parser<T> parser;
    
    /**
     * Constructor requiring Protobuf parser.
     * 
     * Each Protobuf generated class provides a static parser() method:
     * - MessageEvent.parser()
     * - StateUpdateEvent.parser()
     * - PlatformMessageEvent.parser()
     * 
     * @param parser Protobuf parser for specific message type
     */
    public ProtobufDeserializer(Parser<T> parser) {
        this.parser = parser;
    }
    
    /**
     * Deserialize byte array to Protobuf message.
     * 
     * Protobuf parser validates:
     * - Field types match schema (string vs int)
     * - Field numbers are recognized (unknown fields ignored for forward compatibility)
     * - Required fields present (Protobuf 3 has no required fields, but custom validation possible)
     * 
     * @param topic Kafka topic name (unused, required by Deserializer interface)
     * @param data Byte array to deserialize
     * @return Protobuf message instance, or null if data is null
     * @throws SerializationException if bytes are not valid Protobuf for this message type
     */
    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        
        try {
            // Protobuf native deserialization: bytes → Java object
            // Parser validates schema and throws exception if incompatible
            return parser.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            // Wrap Protobuf exception as Kafka SerializationException
            // This triggers Kafka error handling (dead letter queue, retry, etc.)
            throw new SerializationException(
                "Failed to deserialize Protobuf message from topic: " + topic, e);
        }
    }
    
    /**
     * No cleanup needed for Protobuf deserialization (stateless).
     */
    @Override
    public void close() {
        // No resources to release
    }
}
