package com.chat.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Esta classe define a topologia do RabbitMQ (Exchanges, Filas e Bindings).
 * O Spring AMQP irá criar isso automaticamente ao iniciar.
 */
@Configuration
public class RabbitMQConfig {

    // Nomes para nossas "caixas de correio"
    public static final String EXCHANGE_MENSAGENS = "exchanges.mensagens.topico";
    public static final String QUEUE_MENSAGENS_TEXTO = "queue.mensagens.texto";
    public static final String ROUTING_KEY_TEXTO = "rota.texto.#";


    // TODO: ADICIONAR FILAS E ROTAS PARA MENSAGENS DE MIDIA E EVENTOS DE STATUS
    
    /**
     * Define a Exchange principal para onde o Frontend Service enviará as mensagens.
     * Usamos uma TopicExchange para roteamento flexível.
     */
    @Bean
    public TopicExchange exchangeMensages(){
        return new TopicExchange(EXCHANGE_MENSAGENS);
    }

    /**
     * Define a Fila (Queue) onde as mensagens de texto ficarão
     * esperando o Worker consumir.
     */
    @Bean
    public Queue queueMensagemTexto() {
        return new Queue(QUEUE_MENSAGENS_TEXTO, true);
    }


    @Bean 
    public Binding bindindMensagensTexto(){
        return BindingBuilder
        .bind(queueMensagemTexto())
        .to(exchangeMensages())
        .with(ROUTING_KEY_TEXTO);
    }

}
