package com.chat.config;

import com.mongodb.WriteConcern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.WriteResultChecking;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB Configuration
 * 
 * Responsibility: Configures MongoDB connection with replica set support and write concern majority.
 * Does NOT: Handle business logic, manage entities (see model package).
 * 
 * Distributed Systems Concept: Write Concern MAJORITY ensures data durability across replica set
 * members, providing at-least-once delivery guarantee even during network partitions (FR-029).
 * Trade-off: Slightly higher latency (~10-20ms) vs. eventual consistency, acceptable for <100ms p95 target.
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.chat.repository")
public class MongoConfig {

    /**
     * Configures MongoTemplate with write concern MAJORITY for durability.
     * 
     * @param factory MongoDB database factory from Spring Boot autoconfiguration
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
