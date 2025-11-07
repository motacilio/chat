package com.chat.repository;


import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import org.springframework.stereotype.Repository;
import com.chat.model.ChatMessage;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String>{

    Optional<ChatMessage> findByClientMessageId(String clientMessageId);

}


