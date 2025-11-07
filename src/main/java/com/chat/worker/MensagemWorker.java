package com.chat.worker;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chat.config.RabbitMQConfig;
import com.chat.model.ChatMessage;
import com.chat.repository.ChatMessageRepository;

import br.com.meuprojeto.chat.v1.SendTextMessageRequest;


@Component
public class MensagemWorker {
    
    private static final Logger log = LoggerFactory.getLogger(MensagemWorker.class);


    // TODO: Injetar o repositório MONGODB aqui
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    
    @RabbitListener(queues = RabbitMQConfig.QUEUE_MENSAGENS_TEXTO)
    public void consumirMensagensTexto(byte[] payloadBinario){

        SendTextMessageRequest request;
        try{
            request = SendTextMessageRequest.parseFrom(payloadBinario);
            // TODO: Salvar request no MONGODB
        }catch (Exception e) {
        log.error("[WORKER] Falha ao decodificar Protobuf: {}", e.getMessage());            // TODO: verificar possibilidade de mover para uma fila de erros - dead queue
            return;
        }

        String clientId = request.getClientMessageId();
        String conversationId = request.getConversationId();

        //Verifica se a mensagem já foi enviada ou processada
        if(chatMessageRepository.findByClientMessageId(clientId).isPresent()){
            log.warn("[WORKER] Mensagem duplicada recebida (id: {}). Ignorando.", clientId);
            return;
        }

        // Mapear protobuf - request para o modelo  (usar mapper depois)
        ChatMessage.Content content = new ChatMessage.Content(request.getTextBody());

        ChatMessage messageToSave = new ChatMessage(clientId, 
        conversationId, 
        "user-fixo-teste", // Substituir pelo id do remetente real
        content, 
        Instant.now());
        
        try {
            ChatMessage savedMessage = chatMessageRepository.save(messageToSave);
            log.info("[WORKER] Mensagem (id: {}) salva no MongoDB com sucesso (ID do BD: {})",
                    clientId, savedMessage.getId());

            //    TODO (PRÓXIMO PASSO):
            //    Chamar os Conectores (WhatsApp, Telegram) para enviar
            //    a mensagem ao destinatário
            
        } catch (Exception e) {
            log.error("[WORKER] Falha ao salvar mensagem (id: {}) no MongoDB: {}", clientId, e.getMessage());
            // TODO: Re-enfileirar a mensagem (NACK) ou mover para fila de erro
        }

    }
}
