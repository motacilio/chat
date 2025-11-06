package com.chat;

import org.lognet.springboot.grpc.autoconfigure.GRpcAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.context.annotation.Import;


@SpringBootApplication
@Import({
    RabbitAutoConfiguration.class,  // Força o carregamento do RabbitMQ
    GRpcAutoConfiguration.class     // Força o carregamento do gRPC
})
public class ChatApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApiApplication.class, args);
    }
}