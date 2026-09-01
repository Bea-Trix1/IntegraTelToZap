package com.consumertelo.consumer_to_zap.service;

import com.consumertelo.consumer_to_zap.exception.MessageProcessingException;
import com.consumertelo.consumer_to_zap.integration.whatsapp.PermanentWhatsAppException;
import com.consumertelo.consumer_to_zap.integration.whatsapp.TransientWhatsAppException;
import com.consumertelo.consumer_to_zap.integration.whatsapp.WhatsAppService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageProcessingServiceTest {

    private WhatsAppService whatsAppService;
    private MessageProcessingService service;

    @BeforeEach
    void setUp() {
        whatsAppService = Mockito.mock(WhatsAppService.class);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new MessageProcessingService(
                whatsAppService,
                new ObjectMapper(),
                validator,
                new SimpleMeterRegistry(),
                "",
                24,
                1000
        );
    }

    private String payload(String id, String to, String text) {
        return """
                {"messageId":"%s","chatId":123,"from":"produtor-go","to":"%s","text":"%s"}
                """.formatted(id, to, text);
    }

    @Test
    void process_sendsMessage_whenValid() {
        when(whatsAppService.sendMessage(anyString(), anyString(), any(), any())).thenReturn("SM123");

        service.process(payload("id-1", "+5511999999999", "oi"));

        verify(whatsAppService, times(1)).sendMessage("whatsapp:+5511999999999", "oi", null, null);
    }

    @Test
    void process_ignoresDuplicateMessageId() {
        when(whatsAppService.sendMessage(anyString(), anyString(), any(), any())).thenReturn("SM123");

        service.process(payload("dup-id", "+5511999999999", "oi"));
        service.process(payload("dup-id", "+5511999999999", "oi de novo"));

        verify(whatsAppService, times(1)).sendMessage(anyString(), anyString(), any(), any());
    }

    @Test
    void process_throwsNonRetryable_whenJsonMalformed() {
        assertThatThrownBy(() -> service.process("{not-json"))
                .isInstanceOf(MessageProcessingException.class)
                .satisfies(e -> assertThat(((MessageProcessingException) e).isRetryable()).isFalse());

        verify(whatsAppService, never()).sendMessage(anyString(), anyString(), any(), any());
    }

    @Test
    void process_throwsNonRetryable_whenPhoneFormatInvalid() {
        assertThatThrownBy(() -> service.process(payload("id-2", "numero-invalido", "oi")))
                .isInstanceOf(MessageProcessingException.class)
                .satisfies(e -> assertThat(((MessageProcessingException) e).isRetryable()).isFalse());

        verify(whatsAppService, never()).sendMessage(anyString(), anyString(), any(), any());
    }

    @Test
    void process_throwsRetryable_whenWhatsAppFailsTransiently() {
        when(whatsAppService.sendMessage(anyString(), anyString(), any(), any()))
                .thenThrow(new TransientWhatsAppException("timeout", new RuntimeException()));

        assertThatThrownBy(() -> service.process(payload("id-3", "+5511999999999", "oi")))
                .isInstanceOf(MessageProcessingException.class)
                .satisfies(e -> assertThat(((MessageProcessingException) e).isRetryable()).isTrue());
    }

    @Test
    void process_throwsNonRetryable_whenWhatsAppFailsPermanently() {
        when(whatsAppService.sendMessage(anyString(), anyString(), any(), any()))
                .thenThrow(new PermanentWhatsAppException("numero invalido", new RuntimeException()));

        assertThatThrownBy(() -> service.process(payload("id-4", "+5511999999999", "oi")))
                .isInstanceOf(MessageProcessingException.class)
                .satisfies(e -> assertThat(((MessageProcessingException) e).isRetryable()).isFalse());
    }

    @Test
    void process_buildsStatusCallbackUrl_whenBaseUrlAndChatIdPresent() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        MessageProcessingService serviceWithCallback = new MessageProcessingService(
                whatsAppService,
                new ObjectMapper(),
                validator,
                new SimpleMeterRegistry(),
                "https://example.com",
                24,
                1000
        );
        when(whatsAppService.sendMessage(anyString(), anyString(), any(), any())).thenReturn("SM123");

        serviceWithCallback.process(payload("id-5", "+5511999999999", "oi"));

        verify(whatsAppService).sendMessage(
                "whatsapp:+5511999999999",
                "oi",
                null,
                URI.create("https://example.com/webhooks/twilio/status?chatId=123&messageId=id-5"));
    }
}
