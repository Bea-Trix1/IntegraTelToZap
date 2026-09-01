package com.consumertelo.consumer_to_zap.service;

import com.consumertelo.consumer_to_zap.dto.StatusMessageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * P3.3: publica o status de entrega recebido da Twilio na fila que o
 * producer Go consome para fechar o loop de feedback com o usuário do
 * Telegram. Se a fila de status não estiver configurada, o recurso fica
 * desativado silenciosamente (comportamento opcional).
 */
@Service
public class StatusNotificationService {
    private static final Logger log = LoggerFactory.getLogger(StatusNotificationService.class);

    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;
    private final String statusQueueUrl;

    public StatusNotificationService(
            SqsTemplate sqsTemplate,
            ObjectMapper objectMapper,
            @Value("${aws.sqs.status-queue.url:}") String statusQueueUrl
    ) {
        this.sqsTemplate = sqsTemplate;
        this.objectMapper = objectMapper;
        this.statusQueueUrl = statusQueueUrl;
    }

    public void notify(Long chatId, String messageId, String status, String errorCode) {
        if (statusQueueUrl == null || statusQueueUrl.isBlank()) {
            log.debug("Fila de status não configurada, ignorando notificação de status");
            return;
        }
        if (chatId == null) {
            log.debug("Status sem chatId associado, nada para notificar");
            return;
        }

        StatusMessageDTO dto = new StatusMessageDTO();
        dto.setChatId(chatId);
        dto.setMessageId(messageId);
        dto.setStatus(status);
        dto.setErrorCode(errorCode);

        try {
            String json = objectMapper.writeValueAsString(dto);
            sqsTemplate.send(statusQueueUrl, json);
            log.info("Status de entrega publicado: chatId={}, messageId={}, status={}", chatId, messageId, status);
        } catch (Exception e) {
            log.error("Falha ao publicar status de entrega na fila", e);
        }
    }
}
