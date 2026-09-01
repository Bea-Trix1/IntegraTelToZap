package com.consumertelo.consumer_to_zap.service;

import com.consumertelo.consumer_to_zap.dto.MessageDTO;
import com.consumertelo.consumer_to_zap.exception.MessageProcessingException;
import com.consumertelo.consumer_to_zap.integration.whatsapp.PermanentWhatsAppException;
import com.consumertelo.consumer_to_zap.integration.whatsapp.TransientWhatsAppException;
import com.consumertelo.consumer_to_zap.integration.whatsapp.WhatsAppService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MessageProcessingService {
    private static final Logger log = LoggerFactory.getLogger(MessageProcessingService.class);

    private final WhatsAppService whatsAppService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final MeterRegistry meterRegistry;
    private final String statusCallbackBaseUrl;

    // P1.6: dedup com TTL e tamanho limitado (em vez de Set ilimitado, que
    // crescia para sempre e não expirava reenvios legítimos antigos).
    private final Cache<String, Boolean> processedMessages;

    public MessageProcessingService(
            WhatsAppService whatsAppService,
            ObjectMapper objectMapper,
            Validator validator,
            MeterRegistry meterRegistry,
            @Value("${twilio.status-callback-base-url:}") String statusCallbackBaseUrl,
            @Value("${app.dedup.ttl-hours:24}") long dedupTtlHours,
            @Value("${app.dedup.max-size:100000}") long dedupMaxSize
    ) {
        this.whatsAppService = whatsAppService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.meterRegistry = meterRegistry;
        this.statusCallbackBaseUrl = statusCallbackBaseUrl;
        this.processedMessages = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(dedupTtlHours))
                .maximumSize(dedupMaxSize)
                .build();
    }

    /**
     * Processa uma mensagem da fila. Lança {@link MessageProcessingException}
     * com {@code retryable=true} para erros transientes (o listener deve
     * relançar para o SQS reentregar) e {@code retryable=false} para erros
     * permanentes (o listener loga e confirma, sem loop de redelivery).
     */
    public void process(String jsonPayload) {
        MessageDTO messageDTO = parse(jsonPayload);

        String messageId = messageDTO.getId();
        if (messageId == null || messageId.isBlank()) {
            log.warn("Mensagem sem messageId — deduplicação pode não funcionar corretamente");
            messageId = UUID.randomUUID().toString();
        }

        if (processedMessages.getIfPresent(messageId) != null) {
            log.warn("Mensagem duplicada, ignorando: {}", messageId);
            meterRegistry.counter("messages.duplicated").increment();
            return;
        }

        validate(messageDTO);

        String numberWhatsApp = "whatsapp:" + messageDTO.getTo();
        URI statusCallbackUrl = buildStatusCallbackUrl(messageDTO, messageId);

        try {
            whatsAppService.sendMessage(numberWhatsApp, messageDTO.getText(), messageDTO.getMediaUrl(), statusCallbackUrl);
        } catch (TransientWhatsAppException e) {
            meterRegistry.counter("messages.failed", "reason", "transient").increment();
            throw new MessageProcessingException("Falha transiente ao enviar WhatsApp: " + e.getMessage(), e, true);
        } catch (PermanentWhatsAppException e) {
            meterRegistry.counter("messages.failed", "reason", "permanent").increment();
            throw new MessageProcessingException("Falha permanente ao enviar WhatsApp: " + e.getMessage(), e, false);
        }

        processedMessages.put(messageId, Boolean.TRUE);
        meterRegistry.counter("messages.processed").increment();
        log.info("Mensagem processada com sucesso: {}", messageId);
    }

    private MessageDTO parse(String jsonPayload) {
        try {
            return objectMapper.readValue(jsonPayload, MessageDTO.class);
        } catch (JsonProcessingException e) {
            meterRegistry.counter("messages.failed", "reason", "malformed").increment();
            throw new MessageProcessingException("Payload JSON inválido: " + e.getMessage(), e, false);
        }
    }

    private void validate(MessageDTO dto) {
        Set<ConstraintViolation<MessageDTO>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            meterRegistry.counter("messages.failed", "reason", "validation").increment();
            throw new MessageProcessingException("Payload inválido: " + detail, false);
        }
    }

    private URI buildStatusCallbackUrl(MessageDTO dto, String messageId) {
        if (statusCallbackBaseUrl == null || statusCallbackBaseUrl.isBlank() || dto.getChatId() == null) {
            return null;
        }
        String url = statusCallbackBaseUrl.replaceAll("/+$", "")
                + "/webhooks/twilio/status?chatId=" + dto.getChatId()
                + "&messageId=" + messageId;
        return URI.create(url);
    }
}
