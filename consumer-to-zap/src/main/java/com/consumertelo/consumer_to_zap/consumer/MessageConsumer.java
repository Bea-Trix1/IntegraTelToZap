package com.consumertelo.consumer_to_zap.consumer;

import com.consumertelo.consumer_to_zap.exception.MessageProcessingException;
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

        try {
            messageService.process(jsonPayload);
        } catch (MessageProcessingException e) {
            if (e.isRetryable()) {
                // P0.3: não relançar aqui faria o Spring Cloud AWS confirmar
                // (ack) a mensagem mesmo em falha. Relançamos para que ela
                // volte à fila e seja reentregue, até cair na DLQ (P0.4)
                // depois de esgotadas as tentativas.
                log.error("Falha transiente processando mensagem, será reentregue: {}", e.getMessage());
                throw e;
            }
            // Erro permanente: confirma (não relança) para não entrar em
            // loop de redelivery — fica registrado no log para inspeção.
            log.error("Falha permanente processando mensagem, descartando: {}", e.getMessage(), e);
            return;
        }

        log.info("Processamento da mensagem finalizado");
    }
}
