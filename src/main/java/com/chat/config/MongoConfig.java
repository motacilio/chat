package com.chat.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.WriteResultChecking;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.concurrent.TimeUnit;

/**
 * MongoDB Configuration with Replica Set and Connection Pooling
 * 
 * Responsibility: Configures MongoDB connection with replica set support, write concern majority,
 * and connection pooling optimized for horizontal scalability.
 * Does NOT: Handle business logic, manage entities (see model package).
 * 
 * Distributed Systems Concept: Write Concern MAJORITY ensures data durability across replica set
 * members, providing at-least-once delivery guarantee even during network partitions (FR-029).
 * Trade-off: Slightly higher latency (~10-20ms) vs. eventual consistency, acceptable for <100ms p95 target.
 * 
 * Scalability Configuration:
 * - Connection Pool: Max 50 connections per instance (sized for 10,000 concurrent users)
 * - Min Pool Size: 10 warm connections always ready
 * - Max Wait Time: 2000ms prevents thread starvation under load
 * - Max Connection Idle Time: 60 seconds keeps connections fresh
 * - Replica Set: Supports automatic failover and read scaling
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.chat.repository")
public class MongoConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    /**
     * Creates MongoClient with connection pooling and replica set configuration.
     * 
     * @return MongoClient configured for horizontal scalability
     */
    @Bean
    public MongoClient mongoClient() {
        ConnectionString connectionString = new ConnectionString(mongoUri);
        
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            // Connection pooling for scalability (10,000 concurrent users target)
            .applyToConnectionPoolSettings(builder -> builder
                .maxSize(50)                    // Max 50 connections per instance
                .minSize(10)                    // Keep 10 warm connections ready
                .maxWaitTime(2000, TimeUnit.MILLISECONDS)  // Prevent thread starvation
                .maxConnectionIdleTime(60, TimeUnit.SECONDS)  // Refresh idle connections
                .maxConnectionLifeTime(30, TimeUnit.MINUTES)  // Prevent stale connections
            )
            // Socket timeout configuration
            .applyToSocketSettings(builder -> builder
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
            )
            // Cluster settings for replica set
            .applyToClusterSettings(builder -> builder
                .serverSelectionTimeout(5, TimeUnit.SECONDS)
            )
            // Retry writes for transient failures
            .retryWrites(true)
            .build();
        
        return MongoClients.create(settings);
    }

    /**
     * Creates MongoDatabaseFactory using custom MongoClient.
     * 
     * @param mongoClient MongoClient with connection pooling
     * @return MongoDatabaseFactory for Spring Data MongoDB
     */
    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(MongoClient mongoClient) {
        return new SimpleMongoClientDatabaseFactory(mongoClient, "chat");
    }

    /**
     * Configures MongoTemplate with write concern MAJORITY for durability.
     * 
     * @param factory MongoDB database factory from custom configuration
     * @return MongoTemplate configured with MAJORITY write concern
     */
    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory) {
        MongoTemplate template = new MongoTemplate(factory);
        
        // FR-029: Use write concern MAJORITY for data durability across replica set
        template.setWriteConcern(WriteConcern.MAJORITY);
        
        // Enable write result checking to throw exceptions on write failures
        template.setWriteResultChecking(WriteResultChecking.EXCEPTION);
        
        return template;
    }
}
