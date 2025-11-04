package com.chat.config;

import org.springframework.amqp.core.Queue;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_MENSAGENS = "exchanges.mensagens.topico";
    public static final String QUEUE_MENSAGENS_TEXTO = "queue.mensagens.texto";
    public static final String ROUTING_KEY_TEXTO = "rota.texto.#";


    // TODO: ADICIONAR FILAS E ROTAS PARA MENSAGENS DE MIDIA E EVENTOS DE STATUS
    

    @Bean
    public Queue queueMensagemTexto() {
        return new Queue(QUEUE_MENSAGENS_TEXTO, true);
    }


}
