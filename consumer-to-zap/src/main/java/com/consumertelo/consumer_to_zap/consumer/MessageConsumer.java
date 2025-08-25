package com.consumertelo.consumer_to_zap.consumer;

import com.consumertelo.consumer_to_zap.service.MessageProcessingService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MessageConsumer {
    private static final Logger log = LoggerFactory.getLogger(MessageConsumer.class);
    private final MessageProcessingService messageService;

    public MessageConsumer(MessageProcessingService messageService){
        this.messageService = messageService;
    }

    @SqsListener("${aws.sqs.queue.url}")
    public void receiveMessage(String jsonPayload) {
        log.info("Nova mensagem recebida da fila: {}", jsonPayload);

        messageService.process(jsonPayload);
        
        log.info("Processamento da mensagem finalizado: {}", jsonPayload);
    }
}
