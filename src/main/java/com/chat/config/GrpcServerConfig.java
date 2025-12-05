package com.chat.config;

import io.grpc.BindableService;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC Server Configuration
 * 
 * Responsibility: Configures gRPC server with reflection enabled for runtime API discovery.
 * Does NOT: Implement service logic (see grpc package), handle authentication (see AuthenticationInterceptor).
 * 
 * Distributed Systems Concept: gRPC Server Reflection enables clients to discover service methods
 * at runtime without .proto files, useful for tools like grpcurl and debugging (quickstart.md).
 * Protobuf binary serialization provides 30-40% lower latency vs JSON (research.md Decision 1).
 * 
 * Note: The grpc-spring-boot-starter library automatically starts the gRPC server on port 9090.
 * This configuration adds reflection service for development/debugging.
 */
@Configuration
public class GrpcServerConfig {
    
    /**
     * Adds ProtoReflectionService to gRPC server for runtime service discovery.
     * 
     * This enables tools like grpcurl to list services and methods without .proto files:
     * - grpcurl -plaintext localhost:9090 list
     * - grpcurl -plaintext localhost:9090 describe chat_api.v1.ChatService
     * 
     * @return ProtoReflectionService bean (automatically registered by grpc-spring-boot-starter)
     */
    @Bean
    public BindableService protoReflectionService() {
        return ProtoReflectionService.newInstance();
    }
}
